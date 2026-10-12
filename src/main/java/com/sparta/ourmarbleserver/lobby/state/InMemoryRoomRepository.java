package com.sparta.ourmarbleserver.lobby.state;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 서버 메모리에 방 상태를 보관하는 구현.
 * 방 → 플레이어들은 Room.memberIds(), 플레이어 → 방은 여기 색인이 맡는다.
 * 색인은 save / deleteById 때만 갱신되므로, Room을 고친 뒤에는 반드시 save를 불러야 한다.
 */
@Component
public class InMemoryRoomRepository implements RoomRepository {
    private final Map<String, Room> store = new ConcurrentHashMap<>();

    // playerId → 들어 있는 방 id
    private final Map<Long, String> roomIdByPlayer = new HashMap<>();

    // 방마다 지난번 save 때 색인에 올린 멤버. 나간 사람을 찾아 색인에서 빼는 데 쓴다.
    private final Map<String, Set<Long>> indexedMembers = new HashMap<>();

    @Override
    public Optional<Room> findById(String roomId) {
        return Optional.ofNullable(store.get(roomId));
    }

    @Override
    public List<Room> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public synchronized void save(Room room) {
        store.put(room.getRoomId(), room);
        reindex(room.getRoomId(), new HashSet<>(room.memberIds()));
    }

    @Override
    public synchronized void deleteById(String roomId) {
        store.remove(roomId);
        reindex(roomId, Set.of());
    }

    @Override
    public synchronized Optional<String> findRoomIdByPlayerId(long playerId) {
        return Optional.ofNullable(roomIdByPlayer.get(playerId));
    }

    /** 이 방의 색인을 현재 멤버에 맞춘다. 빠진 사람은 지우고, 새로 온 사람은 더한다. */
    private void reindex(String roomId, Set<Long> currentMembers) {
        Set<Long> previousMembers = indexedMembers.getOrDefault(roomId, Set.of());

        for (Long playerId : previousMembers) {
            if (!currentMembers.contains(playerId)) {
                // 이 방을 가리키고 있을 때만 지운다. (이미 다른 방으로 옮겨 간 기록은 건드리지 않는다)
                roomIdByPlayer.remove(playerId, roomId);
            }
        }
        for (Long playerId : currentMembers) {
            roomIdByPlayer.put(playerId, roomId);
        }

        if (currentMembers.isEmpty()) {
            indexedMembers.remove(roomId);
        } else {
            indexedMembers.put(roomId, currentMembers);
        }
    }
}