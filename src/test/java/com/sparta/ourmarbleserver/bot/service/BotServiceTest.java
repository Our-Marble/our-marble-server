package com.sparta.ourmarbleserver.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 봇 번호와 이름 만들기 테스트. 번호 발급은 Redis INCR을 흉내 낸 mock으로 확인한다. */
class BotServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final AtomicLong redisCounter = new AtomicLong();   // Redis의 bot:seq 대신
    private final BotService botService = new BotService(redisTemplate);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(BotService.BOT_SEQ_KEY)).thenAnswer(invocation -> redisCounter.incrementAndGet());
    }

    @Test
    void 봇_번호는_Redis_INCR_값을_음수로_바꾼_것이다() {
        assertThat(botService.nextBotId()).isEqualTo(-1);
        assertThat(botService.nextBotId()).isEqualTo(-2);
        assertThat(botService.nextBotId()).isEqualTo(-3);
        verify(valueOps, times(3)).increment(BotService.BOT_SEQ_KEY);
    }

    @Test
    void 서버가_재시작해도_Redis_값에서_이어서_발급한다() {
        redisCounter.set(5);   // 이전에 5번까지 발급된 상태로 Redis에 남아 있음

        assertThat(botService.nextBotId()).isEqualTo(-6);   // -1부터 다시 시작하지 않는다
    }

    @Test
    void 여러_곳에서_동시에_불러도_번호가_겹치지_않는다() {
        Set<Long> ids = IntStream.range(0, 1000).parallel()
                .mapToObj(i -> botService.nextBotId())
                .collect(Collectors.toSet());

        assertThat(ids).hasSize(1000).allMatch(BotService::isBotId);
    }

    @Test
    void 봇_이름은_비어_있으면_봇1부터_시작한다() {
        assertThat(botService.nextNickname(List.of())).isEqualTo("봇1");
    }

    @Test
    void 이미_쓰는_이름을_건너뛰고_가장_작은_번호를_붙인다() {
        assertThat(botService.nextNickname(List.of("봇1", "봇2"))).isEqualTo("봇3");
        assertThat(botService.nextNickname(List.of("봇1", "봇3"))).isEqualTo("봇2");   // 봇2를 제거한 뒤
        assertThat(botService.nextNickname(List.of("Alice"))).isEqualTo("봇1");        // 사람 닉네임은 상관없다
    }

    @Test
    void 음수_번호만_봇_번호로_본다() {
        assertThat(BotService.isBotId(-1)).isTrue();
        assertThat(BotService.isBotId(0)).isFalse();
        assertThat(BotService.isBotId(7)).isFalse();
    }
}