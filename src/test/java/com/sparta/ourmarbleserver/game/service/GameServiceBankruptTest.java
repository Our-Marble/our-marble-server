package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.game.state.InMemoryGameStateRepository;
import com.sparta.ourmarbleserver.game.state.PropertyState;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.transport.FakeEventPublisher;
import com.sparta.ourmarbleserver.property.domain.BuildingLevel;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import tools.jackson.databind.json.JsonMapper;

/**
 * 파산 테스트. 플레이어 1이 주사위로 남의 땅에 도착했는데 전부 팔아도 통행료에 못 미치는 상태에서 시작한다.
 * 기본 설정: 플레이어 1은 현금 10,000에 땅 101(방콕, 건물 없음, 매각가 25,000)을 가지고,
 * 주사위(1, 2)로 3번 칸(땅 103, 플레이어 2 소유, 호텔이라 통행료 180,000)에 도착한다.
 */
class GameServiceBankruptTest {

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
    private PropertyService propertyService;

    private GameService newService(int playerCount, int... faces) {
        GameDataService data = new GameDataService(JsonMapper.builder().build());
        propertyService = new PropertyService(data);
        GameService service = new GameService(repository, publisher, new DiceService(new FixedRandom(faces)),
                new MoveService(data), new TurnService(data), new EconomyService(), propertyService, data);
        service.startGame(ROOM, LongStream.rangeClosed(1, playerCount).boxed().toList());

        property(103).setOwnerId(2L);
        property(103).setBuildingLevel(BuildingLevel.HOTEL);   // 통행료 180,000
        property(101).setOwnerId(1L);
        state().getPlayerState(1L).setMoney(10_000);
        return service;
    }

    private GameState state() {
        return repository.findById(ROOM).orElseThrow();
    }

    private PropertyState property(int propertyId) {
        return state().getPropertyState(propertyId).orElseThrow();
    }

    @Test
    void 전부_팔아도_통행료에_못_미치면_파산한다() {
        GameService service = newService(2, 1, 2);

        service.rollDice(ROOM, 1L);

        assertThat(state().getPlayerState(1L).isBankrupt()).isTrue();
        assertThat(state().getPlayerState(1L).getFinalRank()).isEqualTo(2);
    }

    @Test
    void 파산하면_현금과_땅_매각가_전부가_땅_주인에게_간다() {
        GameService service = newService(2, 1, 2);
        long liquidated = propertyService.getSellValue(property(101));

        service.rollDice(ROOM, 1L);

        assertThat(state().getPlayerState(1L).getMoney()).isZero();
        assertThat(state().getPlayerState(2L).getMoney()).isEqualTo(GameService.START_MONEY + 10_000 + liquidated);
    }

    @Test
    void 파산하면_가진_땅이_모두_주인_없음과_건설_단계_0으로_초기화된다() {
        GameService service = newService(2, 1, 2);
        property(105).setOwnerId(1L);
        property(105).setBuildingLevel(BuildingLevel.VILLA);   // 투자금 100,000 → 매각가 50,000
        long liquidated = propertyService.getSellValue(property(101)) + propertyService.getSellValue(property(105));

        service.rollDice(ROOM, 1L);

        assertThat(property(101).hasOwner()).isFalse();
        assertThat(property(105).hasOwner()).isFalse();
        assertThat(property(105).getBuildingLevel()).isEqualTo(BuildingLevel.LAND);
        assertThat(property(103).isOwnedBy(2L)).isTrue();
        assertThat(state().getPlayerState(2L).getMoney()).isEqualTo(GameService.START_MONEY + 10_000 + liquidated);
    }

    @Test
    void 파산은_클라에_알리지_않고_주사위_알림만_나간다() {
        GameService service = newService(2, 1, 2);

        service.rollDice(ROOM, 1L);

        assertThat(publisher.types()).containsExactly(MessageType.DICE_ROLLED);
    }

    @Test
    void 두_명_게임에서_한_명이_파산하면_게임이_끝난다() {
        GameService service = newService(2, 1, 2);

        service.rollDice(ROOM, 1L);

        assertThat(state().isGameOver()).isTrue();
    }

    @Test
    void 세_명_게임에서_파산하면_다음_생존자에게_턴이_넘어가고_게임은_계속된다() {
        GameService service = newService(3, 1, 2);

        service.rollDice(ROOM, 1L);

        assertThat(state().getPlayerState(1L).getFinalRank()).isEqualTo(3);
        assertThat(state().isGameOver()).isFalse();
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 더블로_파산해도_같은_플레이어가_다시_굴리지_않는다() {
        GameService service = newService(3, 2, 2);   // 더블 (2, 2) → 4번 칸 (독도, 통행료 300,000)
        property(104).setOwnerId(2L);

        service.rollDice(ROOM, 1L);

        assertThat(state().getPlayerState(1L).isBankrupt()).isTrue();
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state().isDouble()).isFalse();
    }

    @Test
    void 파산한_플레이어가_다시_요청하면_거부된다() {
        GameService service = newService(3, 1, 2);
        service.rollDice(ROOM, 1L);

        assertThatThrownBy(() -> service.rollDice(ROOM, 1L))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_YOUR_TURN));
    }
}