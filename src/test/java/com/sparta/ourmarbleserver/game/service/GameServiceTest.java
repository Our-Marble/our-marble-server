package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.game.dto.DiceRolledPayload;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.game.state.InMemoryGameStateRepository;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.transport.FakeEventPublisher;

import tools.jackson.databind.json.JsonMapper;

class GameServiceTest {

    private static final String ROOM = "r1";

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

    /** 플레이어 1, 2로 게임을 시작한 서비스. 주사위 눈은 faces 순서대로 나온다. */
    private GameService newService(int... faces) {
        GameDataService data = new GameDataService(JsonMapper.builder().build());
        GameService service = new GameService(repository, publisher, new DiceService(new FixedRandom(faces)),
                new MoveService(data), new TurnService(data), new EconomyService(), data);
        service.startGame(ROOM, List.of(1L, 2L));
        return service;
    }

    private GameState state() {
        return repository.findById(ROOM).orElseThrow();
    }

    @Test
    void 게임을_시작하면_초기_상태가_만들어진다() {
        newService();

        GameState state = state();
        assertThat(state.getCurrentPlayerId()).isEqualTo(1L);
        assertThat(state.getRoundNumber()).isEqualTo(1);
        assertThat(state.getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
        assertThat(state.players()).hasSize(2);
        assertThat(state.getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY);
        assertThat(state.getPlayerState(1L).getPosition()).isZero();
        assertThat(state.properties()).hasSize(23);
    }

    @Test
    void 주사위를_굴리면_DICE_ROLLED_알림이_방_전원에게_하나만_나간다() {
        GameService service = newService(1, 2);

        service.rollDice(ROOM, 1L);

        assertThat(publisher.types()).containsExactly(MessageType.DICE_ROLLED);
        assertThat(publisher.last().playerId()).isNull();
        DiceRolledPayload payload = publisher.payloadsOf(MessageType.DICE_ROLLED, DiceRolledPayload.class).get(0);
        assertThat(payload.playerId()).isEqualTo(1L);
        assertThat(payload.dice1()).isEqualTo(1);
        assertThat(payload.dice2()).isEqualTo(2);
    }

    @Test
    void 빈_땅에_도착하면_구매_선택을_기다린다() {
        GameService service = newService(1, 2);   // 0 + 3 = 3번 칸 (땅 103)

        service.rollDice(ROOM, 1L);

        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(3);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_PURCHASE);
        assertThat(state().getCurrentPlayerId()).isEqualTo(1L);
    }

    @Test
    void 내_땅에_도착하면_건설_선택을_기다린다() {
        GameService service = newService(1, 2);
        state().getPropertyState(103).orElseThrow().setOwnerId(1L);

        service.rollDice(ROOM, 1L);

        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_BUILD);
    }

    @Test
    void 황금열쇠_칸에_도착하면_카드_뽑기를_기다린다() {
        GameService service = newService(1, 1);   // 0 + 2 = 2번 칸 (황금열쇠), 더블

        service.rollDice(ROOM, 1L);

        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_DRAW_CARD);
        assertThat(state().isDouble()).isTrue();
    }

    @Test
    void 출발_지점을_지나면_월급이_지급된다() {
        GameService service = newService(1, 2);
        state().getPlayerState(1L).setPosition(30);   // 30 + 3 = 33 → 1번 칸

        service.rollDice(ROOM, 1L);

        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(1);
        assertThat(state().getPlayerState(1L).getMoney())
                .isEqualTo(GameService.START_MONEY + EconomyService.SALARY_AMOUNT);
    }

    @Test
    void handle로도_ROLL_DICE를_처리한다() {
        GameService service = newService(1, 2);

        assertThat(service.types()).containsExactly(MessageType.ROLL_DICE);
        service.handle(MessageType.ROLL_DICE, ROOM, 1L, null);

        assertThat(publisher.types()).containsExactly(MessageType.DICE_ROLLED);
    }

    @Test
    void 내_차례가_아니면_거부되고_상태가_바뀌지_않는다() {
        GameService service = newService(1, 2);

        assertThatThrownBy(() -> service.rollDice(ROOM, 2L))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_YOUR_TURN));

        assertThat(publisher.events()).isEmpty();
        assertThat(state().getPlayerState(2L).getPosition()).isZero();
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 파산한_플레이어의_요청은_거부된다() {
        GameService service = newService(1, 2);
        state().getPlayerState(1L).setBankrupt(true);

        assertThatThrownBy(() -> service.rollDice(ROOM, 1L))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PLAYER_BANKRUPT));
        assertThat(publisher.events()).isEmpty();
    }

    @Test
    void 주사위를_굴릴_차례가_아닌_phase면_거부된다() {
        GameService service = newService(1, 2);
        state().setPhase(TurnPhase.AWAITING_PURCHASE);

        assertThatThrownBy(() -> service.rollDice(ROOM, 1L))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE));
        assertThat(publisher.events()).isEmpty();
    }

    @Test
    void 없는_방이면_거부된다() {
        GameService service = newService(1, 2);

        assertThatThrownBy(() -> service.rollDice("없는방", 1L))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE));
    }
}