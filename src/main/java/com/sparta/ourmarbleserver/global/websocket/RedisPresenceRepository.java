package com.sparta.ourmarbleserver.global.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redis에 접속 기록을 둔다.
 * - ws:presence:{playerId}  "서버id:세션id" (TTL 60초, SessionRegistry가 20초마다 연장)
 * 서버가 갑자기 죽어 종료 처리를 못 해도 TTL이 지나면 기록이 사라진다.
 * 지우기와 연장은 "값이 내 것인지 확인 + 실행"을 Lua로 한 번에 한다.
 */
@Component
@RequiredArgsConstructor
public class RedisPresenceRepository implements PresenceRepository {

    static final String KEY_PREFIX = "ws:presence:";
    static final Duration TTL = Duration.ofSeconds(60);

    // KEYS[1] = presence 키 / ARGV[1] = token, ARGV[2] = TTL(ms) / 반환: 이전 token (없으면 nil)
    // 교체와 이전 값 읽기를 한 번에 해서, 두 서버가 동시에 접속 처리를 해도 각자 정확한 직전 값을 받는다
    private static final RedisScript<String> REPLACE = new DefaultRedisScript<>("""
            local previous = redis.call('GET', KEYS[1])
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            return previous
            """, String.class);

    // KEYS[1] = presence 키 / ARGV[1] = token
    private static final RedisScript<Long> DELETE_IF_MINE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    // KEYS = presence 키들 / ARGV[1..n] = 각 키의 token, ARGV[n+1] = TTL(ms)
    private static final RedisScript<Long> REFRESH_IF_MINE = new DefaultRedisScript<>("""
            local ttl = ARGV[#KEYS + 1]
            local refreshed = 0
            for i, key in ipairs(KEYS) do
                if redis.call('GET', key) == ARGV[i] then
                    redis.call('PEXPIRE', key, ttl)
                    refreshed = refreshed + 1
                end
            end
            return refreshed
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public String markOnline(long playerId, String token) {
        return redisTemplate.execute(REPLACE, List.of(key(playerId)), token, String.valueOf(TTL.toMillis()));
    }

    @Override
    public void markOffline(long playerId, String token) {
        redisTemplate.execute(DELETE_IF_MINE, List.of(key(playerId)), token);
    }

    @Override
    public void refresh(Map<Long, String> tokens) {
        if (tokens.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();
        tokens.forEach((playerId, token) -> {
            keys.add(key(playerId));
            args.add(token);
        });
        args.add(String.valueOf(TTL.toMillis()));
        redisTemplate.execute(REFRESH_IF_MINE, keys, args.toArray());
    }

    @Override
    public boolean isOnline(long playerId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(playerId)));
    }

    private static String key(long playerId) {
        return KEY_PREFIX + playerId;
    }
}