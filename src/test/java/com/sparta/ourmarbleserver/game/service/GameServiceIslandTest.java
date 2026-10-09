package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.dto.GameResult;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.game.state.InMemoryGameStateRepository;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import tools.jackson.databind.json.JsonMapper;

/**
 * 무인도 테스트. 칸 번호: 무인도 8 / 황금열쇠 11 / 땅 112는 12번 칸. 영업정지는 3턴이다.
 * 주사위 눈은 테스트마다 정해 둔 값으로 나온다.
 */
class GameServiceIslandTest {

    private static final String ROOM = "r1";
    private static final int ISLAND_TILE = 8;

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

    /** 플레이어 1이 startPosition에서 faces 눈으로 굴리는 상태의 서비스 */
    private GameService newService(int startPosition, int... faces) {
        GameDataService data = new GameDataService(JsonMapper.builder().build());
        GameService service = new GameService(repository, new DiceService(new FixedRandom(faces)),
                new MoveService(data), new TurnService(data), new EconomyService(), new PropertyService(data), data);
        service.startGame(ROOM, List.of(1L, 2L));
        state().getPlayerState(1L).setPosition(startPosition);
        return service;
    }

    private GameState state() {
        return repository.findById(ROOM).orElseThrow();
    }

    @Test
    void 주사위로_무인도_칸에_도착하면_영업정지가_시작되고_턴이_넘어간다() {
        GameService service = newService(5, 1, 2);   // 5 + 3 = 8

        service.rollDice(1L);

        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(ISLAND_TILE);
        assertThat(state().getPlayerState(1L).getIslandTurnsRemaining()).isEqualTo(TurnService.ISLAND_TURNS);
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 더블로_무인도_칸에_도착해도_턴이_넘어간다() {
        GameService service = newService(6, 1, 1);   // 6 + 2 = 8, 더블

        service.rollDice(1L);

        assertThat(state().getPlayerState(1L).getIslandTurnsRemaining()).isEqualTo(TurnService.ISLAND_TURNS);
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state().isDouble()).isFalse();
    }

    @Test
    void 세_번_연속_더블이면_이동하지_않고_월급_없이_무인도로_간다() {
        GameService service = newService(30, 1, 1);   // 30에서 더블 (원래라면 월급을 받는 이동)
        state().setConsecutiveDoubleCount(2);          // 앞서 더블이 두 번 나온 상태

        GameResult result = service.rollDice(1L);

        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(ISLAND_TILE);
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY);
        assertThat(state().getPlayerState(1L).getIslandTurnsRemaining()).isEqualTo(TurnService.ISLAND_TURNS);
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state().getConsecutiveDoubleCount()).isZero();
        assertThat(result.types()).containsExactly(MessageType.DICE_ROLLED);
    }

    @Test
    void 영업정지_중에_더블이_아니면_탈출에_실패하고_이동하지_않는다() {
        GameService service = newService(ISLAND_TILE, 1, 2);
        state().getPlayerState(1L).setIslandTurnsRemaining(3);

        GameResult result = service.rollDice(1L);

        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(ISLAND_TILE);
        assertThat(state().getPlayerState(1L).getIslandTurnsRemaining()).isEqualTo(2);
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
        assertThat(result.types()).containsExactly(MessageType.DICE_ROLLED);
    }

    @Test
    void 영업정지가_마지막_턴이어도_더블이_아니면_실패로_넘어가고_0이_된다() {
        GameService service = newService(ISLAND_TILE, 1, 2);
        state().getPlayerState(1L).setIslandTurnsRemaining(1);

        service.rollDice(1L);

        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(ISLAND_TILE);
        assertThat(state().getPlayerState(1L).getIslandTurnsRemaining()).isZero();
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
    }

    @Test
    void 영업정지가_끝난_뒤에는_무인도에서_평범하게_이동한다() {
        GameService service = newService(ISLAND_TILE, 1, 2);   // 8 + 3 = 11 (황금열쇠)
        state().getPlayerState(1L).setIslandTurnsRemaining(0);

        service.rollDice(1L);

        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(11);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_DRAW_CARD);
        assertThat(state().getCurrentPlayerId()).isEqualTo(1L);
    }

    @Test
    void 더블이_나오면_탈출하고_그_눈만큼_이동하지만_추가_턴은_없다() {
        GameService service = newService(ISLAND_TILE, 2, 2);   // 8 + 4 = 12 (땅 112)
        state().getPlayerState(1L).setIslandTurnsRemaining(3);

        service.rollDice(1L);

        assertThat(state().getPlayerState(1L).getIslandTurnsRemaining()).isZero();
        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(12);
        assertThat(state().isDouble()).isFalse();
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_PURCHASE);

        service.purchaseProperty(1L, 112, false);   // 거절하고 턴 종료

        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);   // 더블이었지만 추가 턴 없이 넘어감
    }

    @Test
    void 영업정지가_끝난_뒤_무인도_칸에서_더블이_나오면_이동하고_추가_턴도_받는다() {
        GameService service = newService(ISLAND_TILE, 2, 2);   // 8 + 4 = 12 (땅 112)
        state().getPlayerState(1L).setIslandTurnsRemaining(0);

        service.rollDice(1L);

        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(12);
        assertThat(state().isDouble()).isTrue();
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_PURCHASE);

        service.purchaseProperty(1L, 112, false);   // 거절하고 턴 종료

        assertThat(state().getCurrentPlayerId()).isEqualTo(1L);   // 더블이라 같은 플레이어가 다시 굴림
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }
}