package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.card.dto.CardDrawnPayload;
import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.dto.CardData;
import com.sparta.ourmarbleserver.game.dto.GameResult;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.game.state.InMemoryGameStateRepository;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import tools.jackson.databind.json.JsonMapper;

/**
 * 황금열쇠(DRAW_CARD) 테스트. 카드 효과의 값(금액, 이동 칸 수 등)은 cards.json에서 읽어서 쓰므로 값이 바뀌어도 깨지지 않는다.
 * 카드 id: 0 무인도(GoToInspection) / 1 돈 받기(Bonus) / 2 패널티(Penalty) / 3 본사 이동(MoveTo) / 4 칸 이동(MoveBy)
 * 플레이어 1이 2번 칸(황금열쇠)에서 카드를 뽑는 상태에서 시작한다.
 */
class GameServiceCardTest {

    private static final String ROOM = "r1";
    private static final int KEY_TILE = 2;
    private static final int ISLAND_TILE = 8;

    /** 정해 둔 값(주사위 눈, 카드 번호 + 1)을 순서대로 돌려주는 Random */
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
    private final GameDataService data = new GameDataService(JsonMapper.builder().build());

    /** 카드 id의 목록 위치 + 1. FixedRandom에 넣으면 그 카드가 뽑힌다. */
    private int faceOf(int cardId) {
        List<CardData> cards = data.getCards();
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).id() == cardId) {
                return i + 1;
            }
        }
        throw new IllegalArgumentException("없는 카드: " + cardId);
    }

    private CardData card(int cardId) {
        return data.getCards().stream().filter(c -> c.id() == cardId).findFirst().orElseThrow();
    }

    /** 플레이어 1이 황금열쇠 칸에서 카드 뽑기를 기다리는 상태. randoms는 FixedRandom에 들어갈 값이다. */
    private GameService newService(int... randoms) {
        GameService service = new GameService(repository, new DiceService(new FixedRandom(randoms)),
                new MoveService(data), new TurnService(data), new EconomyService(), new PropertyService(data), data);
        service.startGame(ROOM, List.of(1L, 2L));
        state().getPlayerState(1L).setPosition(KEY_TILE);
        state().setPhase(TurnPhase.AWAITING_DRAW_CARD);
        return service;
    }

    private GameState state() {
        return repository.findById(ROOM).orElseThrow();
    }

    @Test
    void 카드를_뽑으면_CARD_DRAWN_알림이_나간다() {
        GameService service = newService(faceOf(1));

        GameResult result = service.drawCard(1L);

        assertThat(result.roomId()).isEqualTo(ROOM);
        assertThat(result.types()).containsExactly(MessageType.CARD_DRAWN);
        CardDrawnPayload payload = result.payloadsOf(MessageType.CARD_DRAWN, CardDrawnPayload.class).get(0);
        assertThat(payload.playerId()).isEqualTo(1L);
        assertThat(payload.cardId()).isEqualTo(1);
    }

    @Test
    void 돈_받기_카드는_현금을_받고_턴이_넘어간다() {
        GameService service = newService(faceOf(1));

        service.drawCard(1L);

        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY + card(1).amount());
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 패널티_카드는_현금을_내고_턴이_넘어간다() {
        GameService service = newService(faceOf(2));

        service.drawCard(1L);

        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY - card(2).amount());
        assertThat(state().getWelfareFund()).isZero();   // 이 카드는 은행으로 간다 (penaltyToFestivalPool=false)
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
    }

    @Test
    void 패널티는_현금이_모자라면_가진_만큼만_내고_파산하지_않는다() {
        GameService service = newService(faceOf(2));
        state().getPlayerState(1L).setMoney(card(2).amount() - 1);

        service.drawCard(1L);

        assertThat(state().getPlayerState(1L).getMoney()).isZero();
        assertThat(state().getPlayerState(1L).isBankrupt()).isFalse();
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
    }

    @Test
    void 지정_칸_이동_카드는_출발_지점을_지나면_월급을_받고_도착_칸을_처리한다() {
        GameService service = newService(faceOf(3));   // 본사(출발 칸)로 이동
        int target = card(3).targetTileId();
        boolean salary = target < KEY_TILE;

        service.drawCard(1L);

        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(target);
        assertThat(state().getPlayerState(1L).getMoney())
                .isEqualTo(GameService.START_MONEY + (salary ? EconomyService.SALARY_AMOUNT : 0));
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);   // 출발 칸 도착 → 턴 종료
    }

    @Test
    void 뒤로_가는_이동_카드는_월급이_없고_도착_칸을_처리한다() {
        GameService service = newService(faceOf(4));   // 3칸 뒤로
        int expected = ((KEY_TILE + card(4).steps()) % 32 + 32) % 32;   // 2 - 3 = 31번 칸 (땅 131, 빈 땅)

        service.drawCard(1L);

        assertThat(card(4).steps()).isNegative();
        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(expected);
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_PURCHASE);
        assertThat(state().getCurrentPlayerId()).isEqualTo(1L);
    }

    @Test
    void 무인도_카드는_월급_없이_무인도로_가서_영업정지가_시작되고_턴이_넘어간다() {
        GameService service = newService(faceOf(0));

        service.drawCard(1L);

        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(ISLAND_TILE);
        assertThat(state().getPlayerState(1L).getIslandTurnsRemaining()).isEqualTo(TurnService.ISLAND_TURNS);
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY);
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
    }

    @Test
    void 더블이어도_무인도_카드로_가면_추가_턴_없이_턴이_넘어간다() {
        GameService service = newService(faceOf(0));
        state().setDouble(true);

        service.drawCard(1L);

        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state().isDouble()).isFalse();
    }

    @Test
    void 더블로_뽑은_돈_받기_카드는_효과_뒤에_같은_플레이어가_다시_굴린다() {
        GameService service = newService(faceOf(1));
        state().setDouble(true);

        service.drawCard(1L);

        assertThat(state().getCurrentPlayerId()).isEqualTo(1L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 주사위로_황금열쇠_칸에_도착한_뒤_카드를_뽑는_한_흐름() {
        GameService service = newService(1, 2, faceOf(1));   // 주사위 (1, 2) 다음에 돈 받기 카드
        state().getPlayerState(1L).setPosition(ISLAND_TILE);   // 8 + 3 = 11 (황금열쇠)
        state().getPlayerState(1L).setIslandTurnsRemaining(0);
        state().setPhase(TurnPhase.AWAITING_ROLL);

        GameResult rolled = service.rollDice(1L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_DRAW_CARD);

        GameResult drawn = service.drawCard(1L);

        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY + card(1).amount());
        assertThat(rolled.types()).containsExactly(MessageType.DICE_ROLLED);
        assertThat(drawn.types()).containsExactly(MessageType.CARD_DRAWN);
    }

    @Test
    void 내_차례가_아니거나_카드를_뽑을_phase가_아니면_거부된다() {
        GameService service = newService(faceOf(1));

        assertThatThrownBy(() -> service.drawCard(2L))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_YOUR_TURN));

        state().setPhase(TurnPhase.AWAITING_ROLL);
        assertThatThrownBy(() -> service.drawCard(1L))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE));
    }
}