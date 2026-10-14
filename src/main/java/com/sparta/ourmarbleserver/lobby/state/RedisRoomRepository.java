package com.sparta.ourmarbleserver.lobby.state;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis에 방 상태를 보관하는 구현. 서버가 여러 대여도 같은 방을 본다.
 * - lobby:room:{roomId}  방 JSON (StoredRoom)
 * - lobby:rooms          방 id 목록 (Set)
 * - lobby:player-room    playerId → roomId (Hash, 한 사람은 방 하나)
 * - lobby:room-seq       방 번호 (INCR)
 * findById는 매번 새 Room을 만들어 돌려주므로, Room을 고친 뒤에는 반드시 save를 불러야 한다.
 * 여러 명령을 차례로 보내므로 원자적이지 않다. 지금은 LobbyService의 synchronized가 한 서버 안의 순서를 지키고,
 * 서버 사이 동시 수정은 추후 분산 락 구성 단계에서 막는다.
 */
@Component
@RequiredArgsConstructor
public class RedisRoomRepository implements RoomRepository {

    private static final String ROOM_KEY_PREFIX = "lobby:room:";
    private static final String ROOM_IDS_KEY = "lobby:rooms";
    private static final String PLAYER_ROOM_KEY = "lobby:player-room";
    private static final String ROOM_SEQ_KEY = "lobby:room-seq";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<Room> findById(String roomId) {
        return findStored(roomId).map(StoredRoom::toRoom);
    }

    @Override
    public List<Room> findAll() {
        Set<String> roomIds = redisTemplate.opsForSet().members(ROOM_IDS_KEY);
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }
        List<String> jsons = redisTemplate.opsForValue()
                .multiGet(roomIds.stream().map(RedisRoomRepository::roomKey).toList()); // 한 번에 다 가져오기
        if (jsons == null) {    // 파이프라인/트랜잭션 모드에서만 null이 온다 (방어 코드)
            return List.of();
        }
        return jsons.stream()
                .filter(Objects::nonNull)   // 목록 조회와 방 조회 사이에 방이 지워지면 그 자리가 null -> 건너뜀
                .map(json -> objectMapper.readValue(json, StoredRoom.class).toRoom())
                .toList();
    }

    @Override
    public void save(Room room) {
        String roomId = room.getRoomId();
        Set<Long> before = storedMemberIds(roomId);   // 덮어쓰기 전 멤버 (나간 사람을 찾으려고)

        redisTemplate.opsForValue().set(roomKey(roomId), objectMapper.writeValueAsString(StoredRoom.from(room))); // 덮어씀
        redisTemplate.opsForSet().add(ROOM_IDS_KEY, roomId);    // SADD: 이미 있으면 무시
        reindex(roomId, before, new HashSet<>(room.memberIds())); // 장부(PlayerId -> roomId)를 새 멤버에게 맞춤
    }

    @Override
    public void deleteById(String roomId) {
        Set<Long> before = storedMemberIds(roomId);

        redisTemplate.delete(roomKey(roomId));
        redisTemplate.opsForSet().remove(ROOM_IDS_KEY, roomId);
        reindex(roomId, before, Set.of());
    }

    // Hash
    @Override
    public Optional<String> findRoomIdByPlayerId(long playerId) {
        return Optional.ofNullable(playerRoom().get(PLAYER_ROOM_KEY, String.valueOf(playerId)));
    }

    @Override
    public String nextRoomId() {
        return "r_" + redisTemplate.opsForValue().increment(ROOM_SEQ_KEY);   // 서버가 여러 대여도 겹치지 않는다
    }

    // JSON to StoredRoom
    private Optional<StoredRoom> findStored(String roomId) {
        String json = redisTemplate.opsForValue().get(roomKey(roomId));
        return Optional.ofNullable(json).map(value -> objectMapper.readValue(value, StoredRoom.class));
    }

    private Set<Long> storedMemberIds(String roomId) {
        return findStored(roomId)
                .map(stored -> stored.members().stream().map(StoredRoom.Member::playerId).collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    /** 장부 B를 현재 멤버에 맞춘다. 빠진 사람은 지우고, 있는 사람은 이 방으로 적는다. */
    private void reindex(String roomId, Set<Long> before, Set<Long> after) {
        HashOperations<String, String, String> hash = playerRoom();
        for (long playerId : before) {
            if (after.contains(playerId)) {
                continue;
            }
            String field = String.valueOf(playerId);
            if (roomId.equals(hash.get(PLAYER_ROOM_KEY, field))) {   // 이미 다른 방으로 옮겨 간 기록은 건드리지 않는다
                hash.delete(PLAYER_ROOM_KEY, field);
            }
        }
        if (!after.isEmpty()) {
            Map<String, String> entries = after.stream()
                    .collect(Collectors.toMap(String::valueOf, id -> roomId));
            hash.putAll(PLAYER_ROOM_KEY, entries);
        }
    }

    private HashOperations<String, String, String> playerRoom() {
        return redisTemplate.opsForHash();
    }

    private static String roomKey(String roomId) {
        return ROOM_KEY_PREFIX + roomId;
    }
}