package com.sparta.ourmarbleserver.bot.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 봇 번호와 이름 만들기 테스트 */
class BotServiceTest {

    private final BotService botService = new BotService();

    @Test
    void 봇_번호는_음수이고_부를_때마다_달라진다() {
        assertThat(botService.nextBotId()).isEqualTo(-1);
        assertThat(botService.nextBotId()).isEqualTo(-2);
        assertThat(botService.nextBotId()).isEqualTo(-3);
    }

    @Test
    void 여러_곳에서_동시에_불러도_번호가_겹치지_않는다() {
        Set<Long> ids = IntStream.range(0, 1000).parallel()
                .mapToObj(i -> botService.nextBotId())
                .collect(Collectors.toSet());

        assertThat(ids).hasSize(1000);
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