package com.sparta.ourmarbleserver.lobby.state;

import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.LobbyErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisRoomLockTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final RedisRoomLock lock = new RedisRoomLock(redisTemplate, 50);   // 테스트라 50ms만 기다린다

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void 락을_잡으면_작업을_실행하고_내_토큰으로_푼다() {
        when(valueOps.setIfAbsent(eq("lock:room:r_1"), anyString(), any(Duration.class))).thenReturn(true);

        String result = lock.call("r_1", () -> "결과");

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(valueOps).setIfAbsent(eq("lock:room:r_1"), token.capture(), eq(RedisRoomLock.LEASE));
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("lock:room:r_1")), eq(token.getValue()));
        assertThat(result).isEqualTo("결과");
    }

    @Test
    void 작업이_실패해도_락을_푼다() {
        when(valueOps.setIfAbsent(eq("lock:room:r_1"), anyString(), any(Duration.class))).thenReturn(true);

        assertThatThrownBy(() -> lock.call("r_1", () -> {
            throw new IllegalStateException("실패");
        })).isInstanceOf(IllegalStateException.class);

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("lock:room:r_1")), anyString());
    }

    @Test
    void 정해진_시간_안에_못_잡으면_ROOM_BUSY이고_작업은_실행하지_않는다() {
        when(valueOps.setIfAbsent(eq("lock:room:r_1"), anyString(), any(Duration.class))).thenReturn(false);
        AtomicBoolean ran = new AtomicBoolean();

        assertThatThrownBy(() -> lock.call("r_1", () -> {
            ran.set(true);
            return null;
        })).isInstanceOfSatisfying(GameException.class,
                e -> assertThat(e.code()).isEqualTo(LobbyErrorCode.ROOM_BUSY));

        assertThat(ran).isFalse();
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), anyString());
    }
}