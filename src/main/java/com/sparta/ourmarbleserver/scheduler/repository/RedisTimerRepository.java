package com.sparta.ourmarbleserver.scheduler.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis에 마감시간을 보관하는 구현. 서버가 여러 대여도 같은 마감시간을 보고, 한 방의 요청은 한 곳에서만 처리된다.
 * - game:timer:deadlines      Sorted Set: member = roomId, score = 다음 요청 수신 마감시간(ms)
 * - game:timer:lease:{roomId} String: 처리 중인 쪽의 토큰 (PX = 리스 시간, 서버가 죽어도 저절로 사라진다)
 * - game:afk:{roomId}         Set: AFK 플레이어 id
 * 확인과 변경이 끼어들지 않도록 Lua 스크립트로 한 번에 실행한다. 시각은 서버 시계가 아니라 Redis의 TIME을 쓴다.
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
