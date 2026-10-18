package com.sparta.ourmarbleserver.scheduler.repository;

import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.RedisGameStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis에 마감시간을 보관하는 구현. 서버가 여러 대여도 같은 마감시간을 보고, 한 방의 요청은 한 곳에서만 처리된다.
 * - game:timer:deadlines      Sorted Set: member = roomId, score = 다음 요청 수신 마감시간(ms)
 * - game:timer:lease:{roomId} String: 처리 중인 쪽의 토큰 (PX = 리스 시간, 서버가 죽어도 저절로 사라진다)
 * - game:afk:{roomId}         Set: AFK 플레이어 id
 * 확인과 변경이 끼어들지 않도록 Lua 스크립트로 한 번에 실행한다. 시각은 서버 시계가 아니라 Redis의 TIME을 쓴다.
 * saveAndSchedule은 게임 상태(game:state:{roomId}, game:player-room)까지 같은 스크립트에서 쓴다.
 * 상태를 어떤 키에 어떤 JSON으로 쓸지는 RedisGameStateRepository.prepareSave가 정한다.
 * (스크립트가 여러 키를 함께 쓰므로 Redis Cluster가 아닌 단일 Redis/센티널 구성을 전제로 한다)
 */
@Component
@RequiredArgsConstructor
public class RedisTimerRepository implements TimerRepository {

    private static final String DEADLINES_KEY = "game:timer:deadlines";
    private static final String LEASE_KEY_PREFIX = "game:timer:lease:";
    private static final String AFK_KEY_PREFIX = "game:afk:";

    /** Redis 서버 시각(ms) */
    private static final String NOW = """
            local t = redis.call('TIME')
            local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            """;

    // KEYS[1] = deadlines, KEYS[2] = lease / ARGV[1] = roomId, ARGV[2] = token, ARGV[3] = leaseMillis
    // 반환: { 상태, 이전 마감시간 }
    private static final RedisScript<List> CLAIM_BEFORE_DEADLINE = new DefaultRedisScript<>(NOW + """
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            local previous = score or '0'
            if score and tonumber(score) <= now then
                return { 'EXPIRED', previous }
            end
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return { 'BUSY', previous }
            end
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
            if score then
                redis.call('ZADD', KEYS[1], now + tonumber(ARGV[3]), ARGV[1])
            end
            return { 'OK', previous }
            """, List.class);

    // KEYS[1] = deadlines, KEYS[2] = lease / ARGV[1] = roomId, ARGV[2] = token, ARGV[3] = leaseMillis
    private static final RedisScript<Long> CLAIM_EXPIRED = new DefaultRedisScript<>(NOW + """
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if (not score) or tonumber(score) > now then
                return 0
            end
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
            redis.call('ZADD', KEYS[1], now + tonumber(ARGV[3]), ARGV[1])
            return 1
            """, Long.class);

    // KEYS[1] = deadlines, KEYS[2] = lease / ARGV[1] = roomId, ARGV[2] = delayMillis, ARGV[3] = token ('' = 리스 확인 안 함)
    private static final RedisScript<Long> SCHEDULE = new DefaultRedisScript<>(NOW + """
            if ARGV[3] ~= '' then
                local current = redis.call('GET', KEYS[2])
                if current and current ~= ARGV[3] then
                    return 0
                end
            end
            redis.call('DEL', KEYS[2])
            redis.call('ZADD', KEYS[1], now + tonumber(ARGV[2]), ARGV[1])
            return 1
            """, Long.class);

    // KEYS[1] = state, KEYS[2] = player-room, KEYS[3] = deadlines, KEYS[4] = lease, KEYS[5] = afk
    // ARGV[1] = roomId, ARGV[2] = 상태 JSON, ARGV[3] = token ('' = 리스 확인 안 함), ARGV[4] = delayMillis (음수 = 타이머 삭제)
    // ARGV[5] = 색인에서 뺄 플레이어 수 n, ARGV[6 .. 5+n] = 뺄 플레이어, ARGV[6+n ..] = 이 방으로 적을 플레이어
    private static final RedisScript<Long> SAVE_AND_SCHEDULE = new DefaultRedisScript<>(NOW + """
            if ARGV[3] ~= '' then
                local current = redis.call('GET', KEYS[4])
                if current and current ~= ARGV[3] then
                    return 0
                end
            end
            redis.call('SET', KEYS[1], ARGV[2])
            local removed = tonumber(ARGV[5])
            for i = 6, 5 + removed do
                if redis.call('HGET', KEYS[2], ARGV[i]) == ARGV[1] then
                    redis.call('HDEL', KEYS[2], ARGV[i])
                end
            end
            for i = 6 + removed, #ARGV do
                redis.call('HSET', KEYS[2], ARGV[i], ARGV[1])
            end
            redis.call('DEL', KEYS[4])
            if tonumber(ARGV[4]) < 0 then
                redis.call('ZREM', KEYS[3], ARGV[1])
                redis.call('DEL', KEYS[5])
            else
                redis.call('ZADD', KEYS[3], now + tonumber(ARGV[4]), ARGV[1])
            end
            return 1
            """, Long.class);

    // KEYS[1] = deadlines, KEYS[2] = lease / ARGV[1] = roomId, ARGV[2] = token, ARGV[3] = previousDeadline
    private static final RedisScript<Long> RESTORE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[2]) ~= ARGV[2] then
                return 0
            end
            redis.call('DEL', KEYS[2])
            if tonumber(ARGV[3]) > 0 then
                redis.call('ZADD', KEYS[1], ARGV[3], ARGV[1])
            end
            return 1
            """, Long.class);

    // KEYS[1] = deadlines / ARGV[1] = limit
    private static final RedisScript<List> FIND_EXPIRED = new DefaultRedisScript<>(NOW + """
            return redis.call('ZRANGE', KEYS[1], '-inf', now, 'BYSCORE', 'LIMIT', 0, tonumber(ARGV[1]))
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisGameStateRepository gameStateRepository;

    @Override
    public Claim claimBeforeDeadline(String roomId, String token, long leaseMillis) {
        List<?> result = redisTemplate.execute(CLAIM_BEFORE_DEADLINE, keys(roomId),
                roomId, token, String.valueOf(leaseMillis));
        ClaimStatus status = ClaimStatus.valueOf(String.valueOf(result.get(0)));
        return new Claim(status, (long) Double.parseDouble(String.valueOf(result.get(1))));
    }

    @Override
    public boolean claimExpired(String roomId, String token, long leaseMillis) {
        Long result = redisTemplate.execute(CLAIM_EXPIRED, keys(roomId), roomId, token, String.valueOf(leaseMillis));
        return result != null && result == 1L;
    }

    @Override
    public boolean schedule(String roomId, long delayMillis, String token) {
        Long result = redisTemplate.execute(SCHEDULE, keys(roomId),
                roomId, String.valueOf(delayMillis), token == null ? "" : token);
        return result != null && result == 1L;
    }

    @Override
    public boolean saveAndSchedule(GameState state, long delayMillis, String token) {
        String roomId = state.getRoomId();
        RedisGameStateRepository.PreparedSave save = gameStateRepository.prepareSave(state);
        List<String> args = new ArrayList<>();
        args.add(roomId);
        args.add(save.json());
        args.add(token == null ? "" : token);
        args.add(String.valueOf(delayMillis));
        args.add(String.valueOf(save.removedPlayerIds().size()));
        args.addAll(save.removedPlayerIds());
        args.addAll(save.activePlayerIds());
        Long result = redisTemplate.execute(SAVE_AND_SCHEDULE,
                List.of(save.stateKey(), save.playerRoomKey(), DEADLINES_KEY, leaseKey(roomId), afkKey(roomId)),
                args.toArray());
        return result != null && result == 1L;
    }

    @Override
    public void restore(String roomId, String token, long previousDeadline) {
        redisTemplate.execute(RESTORE, keys(roomId), roomId, token, String.valueOf(previousDeadline));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> findExpired(int limit) {
        List<String> result = redisTemplate.execute(FIND_EXPIRED, List.of(DEADLINES_KEY), String.valueOf(limit));
        return result == null ? List.of() : result;
    }

    @Override
    public void remove(String roomId) {
        redisTemplate.opsForZSet().remove(DEADLINES_KEY, roomId);
        redisTemplate.delete(List.of(leaseKey(roomId), afkKey(roomId)));
    }

    @Override
    public void addAfk(String roomId, long playerId) {
        redisTemplate.opsForSet().add(afkKey(roomId), String.valueOf(playerId));
    }

    @Override
    public void removeAfk(String roomId, long playerId) {
        redisTemplate.opsForSet().remove(afkKey(roomId), String.valueOf(playerId));
    }

    @Override
    public boolean isAfk(String roomId, long playerId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(afkKey(roomId), String.valueOf(playerId)));
    }

    private static List<String> keys(String roomId) {
        return List.of(DEADLINES_KEY, leaseKey(roomId));
    }

    private static String leaseKey(String roomId) {
        return LEASE_KEY_PREFIX + roomId;
    }

    private static String afkKey(String roomId) {
        return AFK_KEY_PREFIX + roomId;
    }
}
