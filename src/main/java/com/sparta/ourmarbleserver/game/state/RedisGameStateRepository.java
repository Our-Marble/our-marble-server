package com.sparta.ourmarbleserver.game.state;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis에 게임 상태를 보관하는 구현. 서버를 재시작하거나 여러 대여도 같은 게임을 본다.
 * - game:state:{roomId}  게임 상태 JSON (StoredGame)
 * - game:player-room     playerId → roomId (Hash, 진행 중이고 파산하지 않은 플레이어만)
 * findById / findByPlayerId는 매번 새 GameState를 돌려주므로, 상태를 바꾼 뒤에는 반드시 save를 불러야 한다.
 * save는 여러 명령을 차례로 보내므로 원자적이지 않다.
 * 게임 진행 중의 저장은 GameService가 TimerService를 거쳐 하고, RedisTimerRepository가 prepareSave의 결과를
 * 타이머 갱신과 함께 Lua 스크립트 하나로 쓴다. (상태 저장과 마감시간 기록이 한 번에 반영된다)
 */
@Component
@RequiredArgsConstructor
public class RedisGameStateRepository implements GameStateRepository {

    private static final String STATE_KEY_PREFIX = "game:state:";          // String: 게임 상태 JSON
    private static final String PLAYER_ROOM_KEY = "game:player-room";      // Hash: playerId → roomId

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<GameState> findById(String roomId) {
        return findStored(roomId).map(StoredGame::toState);
    }

    @Override
    public Optional<GameState> findByPlayerId(long playerId) {
        String roomId = hashOps().get(PLAYER_ROOM_KEY, String.valueOf(playerId));
        if (roomId == null) {
            return Optional.empty();
        }
        // 색인이 어긋났을 때를 대비해 메모리 구현과 같은 조건을 한 번 더 확인한다
        return findById(roomId)
                .filter(state -> !state.isGameOver())
                .filter(state -> state.players().stream()
                        .anyMatch(p -> p.getPlayerId() == playerId && !p.isBankrupt()));
    }

    /**
     * 상태 하나를 저장할 때 쓸 내용. 다른 저장(타이머)과 한 Lua 스크립트에서 쓰려고 미리 계산해 둔다.
     * @param stateKey          game:state:{roomId}
     * @param playerRoomKey     game:player-room
     * @param json              저장할 상태 JSON
     * @param removedPlayerIds  색인에서 빠질 플레이어 (파산, 게임 종료). 색인 값이 이 방일 때만 지운다.
     * @param activePlayerIds   색인에 이 방으로 적을 플레이어
     */
    public record PreparedSave(String stateKey, String playerRoomKey, String json,
                               List<String> removedPlayerIds, List<String> activePlayerIds) {
    }

    /** save와 같은 내용을 미리 계산한다. (덮어쓰기 전 상태를 읽어서 색인에서 빠질 사람을 정한다) */
    public PreparedSave prepareSave(GameState state) {
        String roomId = state.getRoomId();
        StoredGame stored = StoredGame.from(state);
        Set<Long> before = activePlayerIds(findStored(roomId).orElse(null));   // 덮어쓰기 전 색인 대상
        Set<Long> after = activePlayerIds(stored);
        List<String> removed = before.stream().filter(id -> !after.contains(id)).map(String::valueOf).toList();
        return new PreparedSave(stateKey(roomId), PLAYER_ROOM_KEY, objectMapper.writeValueAsString(stored),
                removed, after.stream().map(String::valueOf).toList());
    }

    @Override
    public void save(GameState state) {
        String roomId = state.getRoomId();
        Set<Long> before = activePlayerIds(findStored(roomId).orElse(null));   // 덮어쓰기 전 색인 대상

        redisTemplate.opsForValue().set(stateKey(roomId), objectMapper.writeValueAsString(StoredGame.from(state)));
        reindex(roomId, before, activePlayerIds(StoredGame.from(state)));
    }

    @Override
    public void deleteById(String roomId) {
        Set<Long> before = activePlayerIds(findStored(roomId).orElse(null));

        redisTemplate.delete(stateKey(roomId));
        reindex(roomId, before, Set.of());
    }


    private Optional<StoredGame> findStored(String roomId) {
        String json = redisTemplate.opsForValue().get(stateKey(roomId));
        return Optional.ofNullable(json).map(value -> objectMapper.readValue(value, StoredGame.class));
    }

    // 색인에 올릴 플레이어: 게임이 안 끝났고 파산하지 않은 사람. 게임이 끝났으면 아무도 없음.
    private static Set<Long> activePlayerIds(StoredGame stored) {
        if (stored == null || stored.gameOver()) {
            return Set.of();
        }
        return stored.players().stream()
                .filter(p -> !p.bankrupt())
                .map(StoredGame.Player::playerId)
                .collect(Collectors.toSet());
    }

    // 색인을 현재 상태에 맞춘다. 빠진 사람(파산, 게임 종료)은 지우고, 남은 사람은 이 방으로 적는다.
    private void reindex(String roomId, Set<Long> before, Set<Long> after) {
        HashOperations<String, String, String> hash = hashOps();
        for (long playerId : before) {
            if (after.contains(playerId)) {
                continue;
            }
            String field = String.valueOf(playerId);
            if (roomId.equals(hash.get(PLAYER_ROOM_KEY, field))) {   // 다른 게임으로 옮겨 간 기록은 건드리지 않는다
                hash.delete(PLAYER_ROOM_KEY, field);
            }
        }
        if (!after.isEmpty()) {
            hash.putAll(PLAYER_ROOM_KEY, after.stream()
                    .collect(Collectors.toMap(String::valueOf, id -> roomId)));
        }
    }

    private HashOperations<String, String, String> hashOps() {
        return redisTemplate.opsForHash();
    }

    private static String stateKey(String roomId) {
        return STATE_KEY_PREFIX + roomId;
    }
}