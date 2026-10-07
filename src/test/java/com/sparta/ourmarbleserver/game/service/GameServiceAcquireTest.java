package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.transport.FakeEventPublisher;
import com.sparta.ourmarbleserver.property.domain.BuildingLevel;
import com.sparta.ourmarbleserver.property.dto.PropertyAcquiredPayload;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import tools.jackson.databind.json.JsonMapper;

/**
 * 인수·거절(ACQUIRE_PROPERTY) 테스트. 플레이어 1이 3번 칸(땅 103 베이징, 플레이어 2 소유)에서
 * 통행료를 낸 뒤 인수를 고르는 상태에서 시작한다. 베이징 땅값 80,000 → 인수가 160,000
 */
class GameServiceAcquireTest {

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
    private final JsonMapper mapper = JsonMapper.builder().build();
    private PropertyService propertyService;

    private GameService newService() {
        GameDataService data = new GameDataService(mapper);
        propertyService = new PropertyService(data);
        GameService service = new GameService(repository, publisher, new DiceService(new FixedRandom(1, 2)),
                new MoveService(data), new TurnService(data), new EconomyService(), propertyService, data);
        service.startGame(ROOM, List.of(1L, 2L));

        GameState state = state();
        state.getPlayerState(1L).setPosition(3);
        property(PROPERTY_ID).setOwnerId(2L);
        state.setPhase(TurnPhase.AWAITING_ACQUIRE);
        return service;
    }

    private GameState state() {
        return repository.findById(ROOM).orElseThrow();
    }

    private PropertyState property(int propertyId) {
        return state().getPropertyState(propertyId).orElseThrow();
    }

    private void assertRejected(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void 인수하면_인수가가_이전_주인에게_이체되고_주인이_바뀐다() {
        GameService service = newService();
        long price = propertyService.getAcquireValue(property(PROPERTY_ID));

        service.acquireProperty(ROOM, 1L, PROPERTY_ID, true);

        assertThat(price).isEqualTo(160_000);
        assertThat(property(PROPERTY_ID).isOwnedBy(1L)).isTrue();
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY - price);
        assertThat(state().getPlayerState(2L).getMoney()).isEqualTo(GameService.START_MONEY + price);
    }

    @Test
    void 인수해도_건물_단계는_그대로다() {
        GameService service = newService();
        property(PROPERTY_ID).setBuildingLevel(BuildingLevel.VILLA);

        service.acquireProperty(ROOM, 1L, PROPERTY_ID, true);

        assertThat(property(PROPERTY_ID).getBuildingLevel()).isEqualTo(BuildingLevel.VILLA);
    }

    @Test
    void 인수하면_PROPERTY_ACQUIRED가_방_전원에게_나가고_턴이_넘어간다() {
        GameService service = newService();

        service.acquireProperty(ROOM, 1L, PROPERTY_ID, true);

        assertThat(publisher.types()).containsExactly(MessageType.PROPERTY_ACQUIRED);
        assertThat(publisher.last().playerId()).isNull();
        PropertyAcquiredPayload payload =
                publisher.payloadsOf(MessageType.PROPERTY_ACQUIRED, PropertyAcquiredPayload.class).get(0);
        assertThat(payload.playerId()).isEqualTo(1L);
        assertThat(payload.propertyId()).isEqualTo(PROPERTY_ID);
        assertThat(payload.isAccept()).isTrue();
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 거절하면_상태는_그대로이고_isAccept_false로_알리고_턴이_넘어간다() {
        GameService service = newService();

        service.acquireProperty(ROOM, 1L, PROPERTY_ID, false);

        assertThat(property(PROPERTY_ID).isOwnedBy(2L)).isTrue();
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY);
        assertThat(publisher.payloadsOf(MessageType.PROPERTY_ACQUIRED, PropertyAcquiredPayload.class)
                .get(0).isAccept()).isFalse();
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
    }

    @Test
    void 더블이면_인수_뒤에_같은_플레이어가_다시_굴린다() {
        GameService service = newService();
        state().setDouble(true);

        service.acquireProperty(ROOM, 1L, PROPERTY_ID, true);

        assertThat(state().getCurrentPlayerId()).isEqualTo(1L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 현금이_인수가보다_모자라면_거부되고_상태가_바뀌지_않는다() {
        GameService service = newService();
        state().getPlayerState(1L).setMoney(159_999);

        assertRejected(() -> service.acquireProperty(ROOM, 1L, PROPERTY_ID, true), ErrorCode.NOT_ENOUGH_MONEY);

        assertThat(property(PROPERTY_ID).isOwnedBy(2L)).isTrue();
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(159_999);
        assertThat(publisher.events()).isEmpty();
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ACQUIRE);
    }

    @Test
    void 인수가와_현금이_딱_같으면_인수할_수_있다() {
        GameService service = newService();
        state().getPlayerState(1L).setMoney(160_000);

        service.acquireProperty(ROOM, 1L, PROPERTY_ID, true);

        assertThat(property(PROPERTY_ID).isOwnedBy(1L)).isTrue();
        assertThat(state().getPlayerState(1L).getMoney()).isZero();
    }

    @Test
    void 내_땅이거나_주인_없는_땅이면_거부된다() {
        GameService service = newService();

        property(PROPERTY_ID).setOwnerId(1L);
        assertRejected(() -> service.acquireProperty(ROOM, 1L, PROPERTY_ID, true), ErrorCode.CANNOT_ACQUIRE);

        property(PROPERTY_ID).setOwnerId(null);
        assertRejected(() -> service.acquireProperty(ROOM, 1L, PROPERTY_ID, true), ErrorCode.CANNOT_ACQUIRE);
        assertThat(publisher.events()).isEmpty();
    }

    @Test
    void 내_말이_그_땅_위에_없으면_거부된다() {
        GameService service = newService();
        property(104).setOwnerId(2L);

        assertRejected(() -> service.acquireProperty(ROOM, 1L, 104, true), ErrorCode.INVALID_PROPERTY);
    }

    @Test
    void 내_차례가_아니거나_인수를_고를_phase가_아니면_거부된다() {
        GameService service = newService();

        assertRejected(() -> service.acquireProperty(ROOM, 2L, PROPERTY_ID, true), ErrorCode.NOT_YOUR_TURN);

        state().setPhase(TurnPhase.AWAITING_ROLL);
        assertRejected(() -> service.acquireProperty(ROOM, 1L, PROPERTY_ID, true), ErrorCode.INVALID_STATE);
    }

    @Test
    void handle로_인수_요청을_처리한다() throws Exception {
        GameService service = newService();

        assertThat(service.types()).contains(MessageType.ACQUIRE_PROPERTY);
        service.handle(MessageType.ACQUIRE_PROPERTY, ROOM, 1L,
                mapper.readTree("{\"propertyId\":103,\"isAccept\":true}"));

        assertThat(property(PROPERTY_ID).isOwnedBy(1L)).isTrue();
    }

    @Test
    void 통행료를_내고_인수까지_한_흐름으로_이어진다() {
        GameService service = newService();
        state().getPlayerState(1L).setPosition(0);
        state().setPhase(TurnPhase.AWAITING_ROLL);
        long toll = propertyService.getToll(property(PROPERTY_ID));
        long price = propertyService.getAcquireValue(property(PROPERTY_ID));

        service.rollDice(ROOM, 1L);   // 주사위 (1, 2) → 3번 칸 도착, 통행료 정산
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ACQUIRE);

        service.acquireProperty(ROOM, 1L, PROPERTY_ID, true);

        assertThat(property(PROPERTY_ID).isOwnedBy(1L)).isTrue();
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY - toll - price);
        assertThat(state().getPlayerState(2L).getMoney()).isEqualTo(GameService.START_MONEY + toll + price);
    }
}