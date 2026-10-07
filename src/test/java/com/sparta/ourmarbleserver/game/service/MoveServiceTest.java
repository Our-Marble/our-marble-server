package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.game.service.MoveService.MoveResult;
import com.sparta.ourmarbleserver.game.state.PlayerState;

import tools.jackson.databind.json.JsonMapper;

class MoveServiceTest {

    private MoveService service;
    private PlayerState player;

    @BeforeEach
    void setUp() {
        service = new MoveService(new GameDataService(JsonMapper.builder().build()));
        player = new PlayerState(1L);
        player.setMoney(500_000);
    }

    @Test
    void 보드는_32칸이다() {
        assertThat(service.getTileCount()).isEqualTo(32);
    }

    @Test
    void 출발_지점을_안_지나면_월급_대상이_아니다() {
        player.setPosition(3);

        MoveResult result = service.moveBy(player, 7);

        assertThat(result.from()).isEqualTo(3);
        assertThat(result.to()).isEqualTo(10);
        assertThat(result.passedStart()).isFalse();
        assertThat(player.getPosition()).isEqualTo(10);
    }

    @Test
    void 출발_지점을_지나면_월급_대상이다() {
        player.setPosition(30);

        MoveResult result = service.moveBy(player, 5);

        assertThat(result.to()).isEqualTo(3);
        assertThat(result.passedStart()).isTrue();
    }

    @Test
    void 출발_칸에_딱_도착해도_월급_대상이다() {
        player.setPosition(30);

        MoveResult result = service.moveBy(player, 2);

        assertThat(result.to()).isZero();
        assertThat(result.passedStart()).isTrue();
    }

    @Test
    void 이동만_하고_현금은_바꾸지_않는다() {
        player.setPosition(30);

        service.moveBy(player, 5);

        assertThat(player.getMoney()).isEqualTo(500_000);
    }

    @Test
    void 직접_이동은_출발_지점을_지나도_월급_대상이_아니다() {
        player.setPosition(30);

        MoveResult result = service.moveDirectly(player, 8);

        assertThat(result.from()).isEqualTo(30);
        assertThat(result.to()).isEqualTo(8);
        assertThat(result.passedStart()).isFalse();
        assertThat(player.getPosition()).isEqualTo(8);
    }
}