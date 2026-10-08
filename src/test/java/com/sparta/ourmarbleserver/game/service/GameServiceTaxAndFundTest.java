package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.game.state.InMemoryGameStateRepository;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.transport.FakeEventPublisher;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import tools.jackson.databind.json.JsonMapper;

/**
 * 출발·세무조사·기부금(적립금) 칸 도착 테스트.
 * 칸 번호: 출발 0 / 기부금 수령 16 / 세무조사 30. 주사위 눈은 테스트마다 정해 둔 값으로 나온다.
 */
class GameServiceTaxAndFundTest {

    private static final String ROOM = "r1";
    private static final int DONATION_TILE = 30;   // 세무조사 (기부금 납부)
    private static final int CHARITY_TILE = 16;    // 기부금 수령 (푸드 페스티벌)

    /** 정해 둔 주사위 눈(1~6)을 순서대로 돌려주는 Random */
    private static class FixedRandom extends Random {
        private final Queue<Integer> faces = new ArrayDeque<>();

        FixedRandom(int... values) {
            for (int v : values) {
                faces.add(v);
            }
        }

        @Override
        public int nextInt(int bound) {
            return faces.remove() - 1;
        }
    }

    private final GameStateRepository repository = new InMemoryGameStateRepository();
    private final FakeEventPublisher publisher = new FakeEventPublisher();

    /** 플레이어 1이 startPosition에서 faces 눈으로 굴리는 상태의 서비스 */
    private GameService newService(int startPosition, int... faces) {
        GameDataService data = new GameDataService(JsonMapper.builder().build());
        GameService service = new GameService(repository, publisher, new DiceService(new FixedRandom(faces)),
                new MoveService(data), new TurnService(data), new EconomyService(), new PropertyService(data), data);
        service.startGame(ROOM, List.of(1L, 2L));
        state().getPlayerState(1L).setPosition(startPosition);
        return service;
    }

    private GameState state() {
        return repository.findById(ROOM).orElseThrow();
    }

    @Test
    void 세무조사_칸에_도착하면_세금이_걷혀_적립금에_쌓이고_턴이_넘어간다() {
        GameService service = newService(27, 1, 2);   // 27 + 3 = 30

        service.rollDice( 1L);

        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(DONATION_TILE);
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY - EconomyService.TAX_AMOUNT);
        assertThat(state().getWelfareFund()).isEqualTo(EconomyService.TAX_AMOUNT);
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 세금은_알림_없이_서버_내부에서만_처리된다() {
        GameService service = newService(27, 1, 2);

        service.rollDice( 1L);

        assertThat(publisher.types()).containsExactly(MessageType.DICE_ROLLED);
    }

    @Test
    void 현금이_세금보다_적으면_가진_만큼만_내고_파산하지_않는다() {
        GameService service = newService(27, 1, 2);
        state().getPlayerState(1L).setMoney(30_000);

        service.rollDice( 1L);

        assertThat(state().getPlayerState(1L).getMoney()).isZero();
        assertThat(state().getWelfareFund()).isEqualTo(30_000);
        assertThat(state().getPlayerState(1L).isBankrupt()).isFalse();
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
    }

    @Test
    void 더블로_세무조사_칸에_도착해도_세금을_내고_같은_플레이어가_다시_굴린다() {
        GameService service = newService(28, 1, 1);   // 28 + 2 = 30, 더블

        service.rollDice( 1L);

        assertThat(state().getWelfareFund()).isEqualTo(EconomyService.TAX_AMOUNT);
        assertThat(state().getCurrentPlayerId()).isEqualTo(1L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 기부금_칸에_도착하면_쌓인_적립금_전액을_받는다() {
        GameService service = newService(13, 1, 2);   // 13 + 3 = 16
        state().setWelfareFund(250_000);

        service.rollDice( 1L);

        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(CHARITY_TILE);
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY + 250_000);
        assertThat(state().getWelfareFund()).isZero();
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
    }

    @Test
    void 적립금이_없으면_아무것도_받지_않고_턴이_넘어간다() {
        GameService service = newService(13, 1, 2);

        service.rollDice( 1L);

        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY);
        assertThat(state().getWelfareFund()).isZero();
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
    }

    @Test
    void 출발_칸에_도착하면_월급만_받고_턴이_넘어간다() {
        GameService service = newService(29, 1, 2);   // 29 + 3 = 32 → 0번 칸

        service.rollDice( 1L);

        assertThat(state().getPlayerState(1L).getPosition()).isZero();
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY + EconomyService.SALARY_AMOUNT);
        assertThat(state().getWelfareFund()).isZero();
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
    }
}