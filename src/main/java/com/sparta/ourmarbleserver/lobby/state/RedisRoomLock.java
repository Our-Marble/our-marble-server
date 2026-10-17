package com.sparta.ourmarbleserver.lobby.state;

import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.LobbyErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Redis로 방을 잠근다.
 * - lock:room:{roomId}  락 주인의 토큰 (5초 뒤 자동 해제: 락을 잡은 서버가 죽어도 영원히 잠기지 않게)
 * 못 잡으면 짧게 기다리며 다시 시도하고, 끝내 못 잡으면 ROOM_BUSY.
 * 풀 때는 "내 토큰일 때만" 지운다. (리스가 지나 다른 서버가 잡은 락을 지우지 않게, Lua로 확인과 삭제를 한 번에)
 */
@Slf4j
@Component
public class RedisRoomLock implements RoomLock {

    static final String KEY_PREFIX = "lock:room:";
    static final Duration LEASE = Duration.ofSeconds(5);
    static final long WAIT_MILLIS = 2_000;
    private static final long RETRY_MILLIS = 20;

    // KEYS[1] = 락 키 / ARGV[1] = 토큰
    private static final RedisScript<Long> UNLOCK = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long waitMillis;

    @Autowired
    public RedisRoomLock(StringRedisTemplate redisTemplate) {
        this(redisTemplate, WAIT_MILLIS);
    }

    /** 테스트에서 기다리는 시간을 줄인다. */
    RedisRoomLock(StringRedisTemplate redisTemplate, long waitMillis) {
        this.redisTemplate = redisTemplate;
        this.waitMillis = waitMillis;
    }

    @Override
    public <T> T call(String roomId, Supplier<T> action) {
        String key = KEY_PREFIX + roomId;
        String token = UUID.randomUUID().toString();
        acquire(key, token);
        try {
            return action.get();
        } finally {
            release(key, token);
        }
    }

    private void acquire(String key, String token) {
        long deadline = System.currentTimeMillis() + waitMillis;
        while (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, token, LEASE))) {
            if (System.currentTimeMillis() >= deadline) {
                throw new GameException(LobbyErrorCode.ROOM_BUSY);
            }
            try {
                Thread.sleep(RETRY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GameException(LobbyErrorCode.ROOM_BUSY);
            }
        }
    }

    private void release(String key, String token) {
        try {
            redisTemplate.execute(UNLOCK, List.of(key), token);
        } catch (RuntimeException e) {
            log.warn("[락] 해제 실패: key={} {}", key, e.getMessage());   // 리스가 지나면 자동으로 풀린다
        }
    }
}