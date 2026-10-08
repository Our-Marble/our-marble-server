package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.game.state.InMemoryGameStateRepository;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.transport.FakeEventPublisher;
import com.sparta.ourmarbleserver.property.dto.PropertyPurchasedPayload;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 땅 구매·거절(PURCHASE_PROPERTY) 테스트. 플레이어 1이 3번 칸(땅 103)에서 구매를 고르는 상태에서 시작한다. */
class GameServicePurchaseTest {

    private static final String ROOM = "r1";
    private static final int PROPERTY_ID = 103;

    private final GameStateRepository repository = new InMemoryGameStateRepository();
    private final FakeEventPublisher publisher = new FakeEventPublisher();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private PropertyService propertyService;

    private GameService newService() {
        GameDataService data = new GameDataService(mapper);
        propertyService = new PropertyService(data);
        GameService service = new GameService(repository, publisher, new DiceService(),
                new MoveService(data), new TurnService(data), new EconomyService(), propertyService, data);
        service.startGame(ROOM, List.of(1L, 2L));

        GameState state = state();
        state.getPlayerState(1L).setPosition(3);
        state.setPhase(TurnPhase.AWAITING_PURCHASE);
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
    void 구매하면_땅값이_차감되고_주인이_등록된다() {
        GameService service = newService();
        long price = propertyService.getLandPrice(PROPERTY_ID);

        service.purchaseProperty(ROOM, 1L, PROPERTY_ID, true);

        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY - price);
        assertThat(state().getPropertyState(PROPERTY_ID).orElseThrow().isOwnedBy(1L)).isTrue();
    }

    @Test
    void 구매하면_PROPERTY_PURCHASED가_방_전원에게_나가고_턴이_넘어간다() {
        GameService service = newService();

        service.purchaseProperty(ROOM, 1L, PROPERTY_ID, true);

        assertThat(publisher.types()).containsExactly(MessageType.PROPERTY_PURCHASED);
        assertThat(publisher.last().playerId()).isNull();
        PropertyPurchasedPayload payload =
                publisher.payloadsOf(MessageType.PROPERTY_PURCHASED, PropertyPurchasedPayload.class).get(0);
        assertThat(payload.playerId()).isEqualTo(1L);
        assertThat(payload.propertyId()).isEqualTo(PROPERTY_ID);
        assertThat(payload.isAccept()).isTrue();
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 거절하면_현금과_주인은_그대로이고_isAccept_false로_알린다() {
        GameService service = newService();

        service.purchaseProperty(ROOM, 1L, PROPERTY_ID, false);

        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY);
        assertThat(state().getPropertyState(PROPERTY_ID).orElseThrow().hasOwner()).isFalse();
        PropertyPurchasedPayload payload =
                publisher.payloadsOf(MessageType.PROPERTY_PURCHASED, PropertyPurchasedPayload.class).get(0);
        assertThat(payload.isAccept()).isFalse();
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
    }

    @Test
    void 더블이면_구매_뒤에_같은_플레이어가_다시_굴린다() {
        GameService service = newService();
        state().setDouble(true);

        service.purchaseProperty(ROOM, 1L, PROPERTY_ID, true);

        assertThat(state().getCurrentPlayerId()).isEqualTo(1L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 현금이_부족하면_거부되고_상태가_바뀌지_않는다() {
        GameService service = newService();
        state().getPlayerState(1L).setMoney(1_000);

        assertRejected(() -> service.purchaseProperty(ROOM, 1L, PROPERTY_ID, true), ErrorCode.NOT_ENOUGH_MONEY);

        assertThat(publisher.events()).isEmpty();
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(1_000);
        assertThat(state().getPropertyState(PROPERTY_ID).orElseThrow().hasOwner()).isFalse();
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_PURCHASE);
        assertThat(state().getCurrentPlayerId()).isEqualTo(1L);
    }

    @Test
    void 현금이_부족해도_거절은_할_수_있다() {
        GameService service = newService();
        state().getPlayerState(1L).setMoney(1_000);

        service.purchaseProperty(ROOM, 1L, PROPERTY_ID, false);

        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
    }

    @Test
    void 이미_주인이_있는_땅이면_거부된다() {
        GameService service = newService();
        state().getPropertyState(PROPERTY_ID).orElseThrow().setOwnerId(2L);

        assertRejected(() -> service.purchaseProperty(ROOM, 1L, PROPERTY_ID, true), ErrorCode.ALREADY_OWNED);
        assertThat(publisher.events()).isEmpty();
    }

    @Test
    void 내_말이_그_땅_위에_없으면_거부된다() {
        GameService service = newService();

        assertRejected(() -> service.purchaseProperty(ROOM, 1L, 104, true), ErrorCode.INVALID_PROPERTY);
        assertThat(publisher.events()).isEmpty();
    }

    @Test
    void 없는_땅_번호면_거부된다() {
        GameService service = newService();

        assertRejected(() -> service.purchaseProperty(ROOM, 1L, 999, true), ErrorCode.INVALID_PROPERTY);
    }

    @Test
    void 내_차례가_아니면_거부된다() {
        GameService service = newService();

        assertRejected(() -> service.purchaseProperty(ROOM, 2L, PROPERTY_ID, true), ErrorCode.NOT_YOUR_TURN);
        assertThat(publisher.events()).isEmpty();
    }

    @Test
    void 구매를_고를_phase가_아니면_거부된다() {
        GameService service = newService();
        state().setPhase(TurnPhase.AWAITING_ROLL);

        assertRejected(() -> service.purchaseProperty(ROOM, 1L, PROPERTY_ID, true), ErrorCode.INVALID_STATE);
    }

    @Test
    void handle로_구매_요청을_처리한다() throws Exception {
        GameService service = newService();

        assertThat(service.types()).contains(MessageType.PURCHASE_PROPERTY);
        service.handle(MessageType.PURCHASE_PROPERTY, ROOM, 1L,
                mapper.readTree("{\"propertyId\":103,\"isAccept\":true}"));

        assertThat(state().getPropertyState(PROPERTY_ID).orElseThrow().isOwnedBy(1L)).isTrue();
    }

    @Test
    void 요청_본문에_isAccept가_없으면_거부된다() throws Exception {
        GameService service = newService();
        JsonNode body = mapper.readTree("{\"propertyId\":103}");

        assertRejected(() -> service.handle(MessageType.PURCHASE_PROPERTY, ROOM, 1L, body), ErrorCode.INVALID_STATE);
        assertThat(publisher.events()).isEmpty();
    }
}