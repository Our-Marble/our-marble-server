package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.dto.DestinationChosenPayload;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.game.state.InMemoryGameStateRepository;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.transport.FakeEventPublisher;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import tools.jackson.databind.json.JsonMapper;

/** 세계여행(CHOOSE_DESTINATION) 테스트. 세계여행 칸은 24번이다. */
class GameServiceWorldTravelTest {

    private static final String ROOM = "r1";
    private static final int WORLD_TRAVEL_TILE = 24;

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
    private final JsonMapper mapper = JsonMapper.builder().build();
    private PropertyService propertyService;

    private GameService newService(int... faces) {
        GameDataService data = new GameDataService(mapper);
        propertyService = new PropertyService(data);
        GameService service = new GameService(repository, publisher, new DiceService(new FixedRandom(faces)),
                new MoveService(data), new TurnService(data), new EconomyService(), propertyService, data);
        service.startGame(ROOM, List.of(1L, 2L));
        return service;
    }

    /** 플레이어 1이 세계여행 칸에서 목적지 선택을 기다리는 상태로 만든다. */
    private GameService newServiceChoosing(int... faces) {
        GameService service = newService(faces);
        state().getPlayerState(1L).setPosition(WORLD_TRAVEL_TILE);
        state().setPhase(TurnPhase.AWAITING_DESTINATION);
        return service;
    }

    private GameState state() {
        return repository.findById(ROOM).orElseThrow();
    }

    private void assertRejected(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void 세계여행_칸에_도착하면_턴이_넘어간다() {
        GameService service = newService(1, 2);
        state().getPlayerState(1L).setPosition(21);   // 21 + 3 = 24

        service.rollDice(ROOM, 1L);

        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(WORLD_TRAVEL_TILE);
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 더블로_세계여행_칸에_도착해도_턴이_넘어간다() {
        GameService service = newService(1, 1);
        state().getPlayerState(1L).setPosition(22);   // 22 + 2 = 24, 더블

        service.rollDice(ROOM, 1L);

        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state().isDouble()).isFalse();
    }

    @Test
    void 세계여행_칸에서_시작한_턴은_목적지_선택을_기다린다() {
        GameService service = newService(1, 2, 1, 2);
        state().getPlayerState(1L).setPosition(21);

        service.rollDice(ROOM, 1L);               // 플레이어 1: 세계여행 칸에 도착, 턴 넘어감
        service.rollDice(ROOM, 2L);               // 플레이어 2: 3번 칸(땅 103, 빈 땅)에 도착
        service.purchaseProperty(ROOM, 2L, 103, false);   // 거절하고 턴 종료

        assertThat(state().getCurrentPlayerId()).isEqualTo(1L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_DESTINATION);
    }

    @Test
    void 목적지를_고르면_월급_없이_이동하고_도착_칸을_처리한다() {
        GameService service = newServiceChoosing();

        service.chooseDestination(ROOM, 1L, 3);   // 3번 칸 (땅 103, 빈 땅)

        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(3);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_PURCHASE);
        assertThat(state().getCurrentPlayerId()).isEqualTo(1L);
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY);
    }

    @Test
    void 출발_지점을_지나는_방향의_목적지여도_월급은_없다() {
        GameService service = newServiceChoosing();

        service.chooseDestination(ROOM, 1L, 1);   // 24 → 1은 출발 지점을 지나는 방향

        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(1);
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY);
    }

    @Test
    void 목적지_선택은_DESTINATION_CHOSEN을_방_전원에게_보낸다() {
        GameService service = newServiceChoosing();

        service.chooseDestination(ROOM, 1L, 3);

        assertThat(publisher.types()).containsExactly(MessageType.DESTINATION_CHOSEN);
        assertThat(publisher.last().playerId()).isNull();
        DestinationChosenPayload payload =
                publisher.payloadsOf(MessageType.DESTINATION_CHOSEN, DestinationChosenPayload.class).get(0);
        assertThat(payload.playerId()).isEqualTo(1L);
        assertThat(payload.destinationPosition()).isEqualTo(3);
    }

    @Test
    void 목적지가_남의_땅이면_통행료를_내고_인수_선택으로_이어진다() {
        GameService service = newServiceChoosing();
        state().getPropertyState(103).orElseThrow().setOwnerId(2L);
        long toll = propertyService.getToll(state().getPropertyState(103).orElseThrow());

        service.chooseDestination(ROOM, 1L, 3);

        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY - toll);
        assertThat(state().getPlayerState(2L).getMoney()).isEqualTo(GameService.START_MONEY + toll);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ACQUIRE);
    }

    @Test
    void 보드_밖의_목적지는_거부되고_상태가_바뀌지_않는다() {
        GameService service = newServiceChoosing();

        assertRejected(() -> service.chooseDestination(ROOM, 1L, -1), ErrorCode.INVALID_PROPERTY);
        assertRejected(() -> service.chooseDestination(ROOM, 1L, 32), ErrorCode.INVALID_PROPERTY);

        assertThat(state().getPlayerState(1L).getPosition()).isEqualTo(WORLD_TRAVEL_TILE);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_DESTINATION);
        assertThat(publisher.events()).isEmpty();
    }

    @Test
    void 내_차례가_아니거나_목적지를_고를_phase가_아니면_거부된다() {
        GameService service = newServiceChoosing();

        assertRejected(() -> service.chooseDestination(ROOM, 2L, 3), ErrorCode.NOT_YOUR_TURN);

        state().setPhase(TurnPhase.AWAITING_ROLL);
        assertRejected(() -> service.chooseDestination(ROOM, 1L, 3), ErrorCode.INVALID_STATE);
    }

}