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
import com.sparta.ourmarbleserver.game.state.PropertyState;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.transport.FakeEventPublisher;
import com.sparta.ourmarbleserver.property.domain.BuildingLevel;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import tools.jackson.databind.json.JsonMapper;

/**
 * 남의 땅 도착 시 통행료 정산 테스트.
 * 플레이어 1이 주사위(1, 2)로 3번 칸(땅 103)에 도착하고, 땅 103은 플레이어 2의 땅인 상태에서 시작한다.
 */
class GameServiceTollTest {

    private static final String ROOM = "r1";
    private static final int PROPERTY_ID = 103;

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

    private GameService newService() {
        GameDataService data = new GameDataService(JsonMapper.builder().build());
        propertyService = new PropertyService(data);
        GameService service = new GameService(repository, publisher, new DiceService(new FixedRandom(1, 2)),
                new MoveService(data), new TurnService(data), new EconomyService(), propertyService, data);
        service.startGame(ROOM, List.of(1L, 2L));
        property().setOwnerId(2L);
        return service;
    }

    private GameState state() {
        return repository.findById(ROOM).orElseThrow();
    }

    private PropertyState property() {
        return state().getPropertyState(PROPERTY_ID).orElseThrow();
    }

    @Test
    void 통행료가_내_현금에서_땅_주인에게_이체된다() {
        GameService service = newService();
        long toll = propertyService.getToll(property());

        service.rollDice( 1L);

        assertThat(toll).isPositive();
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY - toll);
        assertThat(state().getPlayerState(2L).getMoney()).isEqualTo(GameService.START_MONEY + toll);
    }

    @Test
    void 통행료는_알림_없이_서버_내부에서만_처리된다() {
        GameService service = newService();

        service.rollDice( 1L);

        assertThat(publisher.types()).containsExactly(MessageType.DICE_ROLLED);
    }

    @Test
    void 건물이_있으면_단계에_맞는_통행료를_낸다() {
        GameService service = newService();
        property().setBuildingLevel(BuildingLevel.HOTEL);
        long toll = propertyService.getToll(property());

        service.rollDice( 1L);

        assertThat(toll).isGreaterThan(propertyService.getToll(landLevelCopy()));
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY - toll);
    }

    @Test
    void 통행료를_내고도_인수가만큼_현금이_남으면_인수_선택을_기다린다() {
        GameService service = newService();
        long toll = propertyService.getToll(property());
        long acquire = propertyService.getAcquireValue(property());
        state().getPlayerState(1L).setMoney(toll + acquire);   // 통행료를 내면 인수가와 딱 같다

        service.rollDice( 1L);

        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ACQUIRE);
        assertThat(state().getCurrentPlayerId()).isEqualTo(1L);
    }

    @Test
    void 통행료를_내고_인수가에_못_미치면_턴이_끝난다() {
        GameService service = newService();
        long toll = propertyService.getToll(property());
        long acquire = propertyService.getAcquireValue(property());
        state().getPlayerState(1L).setMoney(toll + acquire - 1);

        service.rollDice( 1L);

        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 현금이_모자라도_전체_매각으로_메울_수_있으면_매각을_기다린다() {
        GameService service = newService();
        long toll = propertyService.getToll(property());
        state().getPlayerState(1L).setMoney(toll - 1);
        state().getPropertyState(101).orElseThrow().setOwnerId(1L);   // 매각가가 통행료를 메우고도 남는 땅

        service.rollDice( 1L);

        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_SELL);
        assertThat(state().getCurrentPlayerId()).isEqualTo(1L);
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(toll - 1);   // 통행료는 아직 안 냄
        assertThat(state().getPlayerState(2L).getMoney()).isEqualTo(GameService.START_MONEY);
    }

    @Test
    void 전부_팔아도_통행료에_못_미치면_매각을_기다리지_않는다() {
        GameService service = newService();
        state().getPlayerState(1L).setMoney(0);   // 가진 땅도 없다

        service.rollDice( 1L);

        assertThat(state().getPlayerState(1L).isBankrupt()).isTrue();
    }

    /** 같은 땅의 건물 없는 상태 (비교용) */
    private PropertyState landLevelCopy() {
        PropertyState copy = new PropertyState(PROPERTY_ID);
        copy.setOwnerId(2L);
        copy.setBuildingLevel(BuildingLevel.LAND);
        return copy;
    }
}