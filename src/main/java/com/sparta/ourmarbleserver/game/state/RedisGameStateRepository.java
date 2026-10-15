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
 * 여러 명령을 차례로 보내므로 원자적이지 않다. (서버 사이 동시 수정은 분산 락 단계에서 막는다)
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