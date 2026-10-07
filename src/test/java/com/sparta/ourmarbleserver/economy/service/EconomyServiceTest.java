package com.sparta.ourmarbleserver.economy.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.state.PlayerState;

class EconomyServiceTest {

    private final EconomyService service = new EconomyService();

    @Test
    void 월급을_현금에_더하고_지급액을_돌려준다() {
        PlayerState player = new PlayerState(1L);
        player.setMoney(500_000);

        long paid = service.paySalary(player);

        assertThat(paid).isEqualTo(EconomyService.SALARY_AMOUNT);
        assertThat(player.getMoney()).isEqualTo(600_000);
    }

    @Test
    void 월급은_받을_때마다_쌓인다() {
        PlayerState player = new PlayerState(1L);

        service.paySalary(player);
        service.paySalary(player);

        assertThat(player.getMoney()).isEqualTo(2 * EconomyService.SALARY_AMOUNT);
    }
}