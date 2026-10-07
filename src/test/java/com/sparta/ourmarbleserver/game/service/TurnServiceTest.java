package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.PlayerState;
import com.sparta.ourmarbleserver.game.state.TurnPhase;

import tools.jackson.databind.json.JsonMapper;

class TurnServiceTest {

    private static final int WORLD_TRAVEL_TILE = 24;

    private TurnService service;
    private GameState state;

    @BeforeEach
    void setUp() {
        service = new TurnService(new GameDataService(JsonMapper.builder().build()));
        state = new GameState("r1");
        for (long id = 1; id <= 3; id++) {
            state.addPlayer(new PlayerState(id));
        }
        state.setPlayerOrder(List.of(1L, 2L, 3L));
    }

    @Test
    void 첫_턴은_1라운드이고_주사위_대기_상태다() {
        service.startTurn(state, 1L);

        assertThat(state.getCurrentPlayerId()).isEqualTo(1L);
        assertThat(state.getTurnNumber()).isEqualTo(1);
        assertThat(state.getRoundNumber()).isEqualTo(1);
        assertThat(state.getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 턴은_순서대로_넘어간다() {
        service.startTurn(state, 1L);

        service.endTurn(state);
        assertThat(state.getCurrentPlayerId()).isEqualTo(2L);
        service.endTurn(state);
        assertThat(state.getCurrentPlayerId()).isEqualTo(3L);
        service.endTurn(state);
        assertThat(state.getCurrentPlayerId()).isEqualTo(1L);
    }

    @Test
    void 한_바퀴_돌아_첫_플레이어로_오면_라운드가_오른다() {
        service.startTurn(state, 1L);
        service.endTurn(state);
        service.endTurn(state);
        assertThat(state.getRoundNumber()).isEqualTo(1);

        service.endTurn(state);   // 다시 1번 플레이어

        assertThat(state.getRoundNumber()).isEqualTo(2);
    }

    @Test
    void 파산한_플레이어는_건너뛴다() {
        service.startTurn(state, 1L);
        state.getPlayerState(2L).setBankrupt(true);

        service.endTurn(state);

        assertThat(state.getCurrentPlayerId()).isEqualTo(3L);
    }

    @Test
    void 더블이면_같은_플레이어가_다시_굴리고_더블_상태는_유지된다() {
        service.startTurn(state, 1L);
        state.setDouble(true);
        state.setConsecutiveDoubleCount(2);
        state.setPhase(TurnPhase.TURN_END);

        service.endTurn(state);

        assertThat(state.getCurrentPlayerId()).isEqualTo(1L);
        assertThat(state.getTurnNumber()).isEqualTo(1);
        assertThat(state.getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
        assertThat(state.getConsecutiveDoubleCount()).isEqualTo(2);
    }

    @Test
    void 더블이_아니면_다음_플레이어로_넘어가며_더블_상태가_초기화된다() {
        service.startTurn(state, 1L);
        state.setDouble(false);
        state.setConsecutiveDoubleCount(0);

        service.endTurn(state);

        assertThat(state.getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state.getTurnNumber()).isEqualTo(2);
        assertThat(state.isDouble()).isFalse();
        assertThat(state.getConsecutiveDoubleCount()).isZero();
    }

    @Test
    void 더블이어도_강제로_넘기면_다음_플레이어로_간다() {
        service.startTurn(state, 1L);
        state.setDouble(true);

        service.passTurn(state);

        assertThat(state.getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state.isDouble()).isFalse();
    }

    @Test
    void 세계여행_칸에서_턴이_시작되면_목적지_선택을_기다린다() {
        state.getPlayerState(1L).setPosition(WORLD_TRAVEL_TILE);

        service.startTurn(state, 1L);

        assertThat(state.getPhase()).isEqualTo(TurnPhase.AWAITING_DESTINATION);
    }

    @Test
    void 최대_라운드를_넘기면_게임이_끝나고_턴은_바뀌지_않는다() {
        service.startTurn(state, 3L);
        state.setRoundNumber(TurnService.MAX_ROUND);

        service.endTurn(state);   // 3번 → 1번이면 31라운드

        assertThat(state.isGameOver()).isTrue();
        assertThat(state.getCurrentPlayerId()).isEqualTo(3L);
    }

    @Test
    void 한_명만_남으면_게임이_끝난다() {
        service.startTurn(state, 1L);
        state.getPlayerState(2L).setBankrupt(true);
        state.getPlayerState(3L).setBankrupt(true);

        service.endTurn(state);

        assertThat(state.isGameOver()).isTrue();
    }
}