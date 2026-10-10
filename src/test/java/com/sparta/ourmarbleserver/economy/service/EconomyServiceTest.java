package com.sparta.ourmarbleserver.economy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.ourmarbleserver.game.state.GameState;
import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.state.PlayerState;

class EconomyServiceTest {

    private final EconomyService service = new EconomyService();

    @Test
    void 월급을_현금에_더하고_지급액을_돌려준다() {
        PlayerState player = new PlayerState(1L);
        player.setMoney(500_000);

        long paid = service.paySalary(player, EconomyService.SALARY_AMOUNT);

        assertThat(paid).isEqualTo(EconomyService.SALARY_AMOUNT);
        assertThat(player.getMoney()).isEqualTo(600_000);
    }

    @Test
    void 월급은_받을_때마다_쌓인다() {
        PlayerState player = new PlayerState(1L);

        service.paySalary(player, EconomyService.SALARY_AMOUNT);
        service.paySalary(player, EconomyService.SALARY_AMOUNT);

        assertThat(player.getMoney()).isEqualTo(2 * EconomyService.SALARY_AMOUNT);
    }

    @Test
    void 현금을_이체한다() {
        PlayerState payer = new PlayerState(1L);
        payer.setMoney(500_000);
        PlayerState receiver = new PlayerState(2L);
        receiver.setMoney(500_000);

        service.transfer(payer, receiver, 20_000);

        assertThat(payer.getMoney()).isEqualTo(480_000);
        assertThat(receiver.getMoney()).isEqualTo(520_000);
    }

    @Test
    void 세금은_적립금에_쌓이고_낸_금액을_돌려준다() {
        GameState state = new GameState("r1");
        PlayerState player = new PlayerState(1L);
        player.setMoney(500_000);

        long paid = service.payTax(state, player, 100_000);

        assertThat(paid).isEqualTo(100_000);
        assertThat(player.getMoney()).isEqualTo(400_000);
        assertThat(state.getWelfareFund()).isEqualTo(100_000);
    }

    @Test
    void 현금이_세금보다_적으면_가진_만큼만_낸다() {
        GameState state = new GameState("r1");
        PlayerState player = new PlayerState(1L);
        player.setMoney(30_000);

        long paid = service.payTax(state, player, 100_000);

        assertThat(paid).isEqualTo(30_000);
        assertThat(player.getMoney()).isZero();
        assertThat(state.getWelfareFund()).isEqualTo(30_000);
    }

    @Test
    void 현금이_없으면_세금을_내지_않는다() {
        GameState state = new GameState("r1");
        PlayerState player = new PlayerState(1L);

        assertThat(service.payTax(state, player, 100_000)).isZero();
        assertThat(state.getWelfareFund()).isZero();
    }

    @Test
    void 적립금_전액을_받으면_적립금은_0이_된다() {
        GameState state = new GameState("r1");
        state.setWelfareFund(250_000);
        PlayerState player = new PlayerState(1L);
        player.setMoney(100_000);

        long received = service.receiveWelfareFund(state, player);

        assertThat(received).isEqualTo(250_000);
        assertThat(player.getMoney()).isEqualTo(350_000);
        assertThat(state.getWelfareFund()).isZero();
    }

    @Test
    void 적립금이_없으면_받는_금액은_0이다() {
        GameState state = new GameState("r1");
        PlayerState player = new PlayerState(1L);
        player.setMoney(100_000);

        assertThat(service.receiveWelfareFund(state, player)).isZero();
        assertThat(player.getMoney()).isEqualTo(100_000);
    }

    @Test
    void 현금_한도_안에서만_낸다() {
        PlayerState player = new PlayerState(1L);
        player.setMoney(30_000);

        long paid = service.payWithinBalance(player, 100_000);

        assertThat(paid).isEqualTo(30_000);
        assertThat(player.getMoney()).isZero();
    }

    @Test
    void 벌금은_은행으로_가거나_적립금에_쌓인다() {
        GameState state = new GameState("r1");
        PlayerState player = new PlayerState(1L);
        player.setMoney(500_000);

        service.payPenalty(state, player, 100_000, false);
        assertThat(player.getMoney()).isEqualTo(400_000);
        assertThat(state.getWelfareFund()).isZero();

        service.payPenalty(state, player, 50_000, true);
        assertThat(player.getMoney()).isEqualTo(350_000);
        assertThat(state.getWelfareFund()).isEqualTo(50_000);
    }
}