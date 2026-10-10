package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.dto.GameResult;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.game.state.InMemoryGameStateRepository;
import com.sparta.ourmarbleserver.game.state.PropertyState;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.property.domain.BuildingLevel;
import com.sparta.ourmarbleserver.property.dto.PropertiesSoldPayload;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import tools.jackson.databind.json.JsonMapper;

/**
 * 매각(SELL_PROPERTIES) 테스트.
 * 플레이어 1이 3번 칸(땅 103, 플레이어 2 소유)에 서 있고 통행료보다 현금이 1원 모자란 채 매각을 기다리는 상태에서 시작한다.
 * 플레이어 1은 땅 101(방콕)과 105(타이베이)를 가지고 있다. (둘 다 건물 없음, 매각가 25,000)
 */
class GameServiceSellTest {

    private static final String ROOM = "r1";

    private final GameStateRepository repository = new InMemoryGameStateRepository();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private PropertyService propertyService;

    private GameService newService() {
        GameDataService data = new GameDataService(mapper);
        propertyService = new PropertyService(data);
        GameService service = new GameService(repository, new DiceService(),
                new MoveService(data), new TurnService(data), new EconomyService(), propertyService, data);
        service.startGame(ROOM, List.of(1L, 2L));

        GameState state = state();
        state.getPlayerState(1L).setPosition(3);
        property(103).setOwnerId(2L);
        property(101).setOwnerId(1L);
        property(105).setOwnerId(1L);
        state.getPlayerState(1L).setMoney(toll() - 1);
        state.setPhase(TurnPhase.AWAITING_SELL);
        return service;
    }

    private GameState state() {
        return repository.findById(ROOM).orElseThrow();
    }

    private PropertyState property(int propertyId) {
        return state().getPropertyState(propertyId).orElseThrow();
    }

    private long toll() {
        return propertyService.getToll(property(103));
    }

    private void assertRejected(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void 매각하면_땅이_초기화되고_매각가가_입금되고_통행료가_이체된다() {
        GameService service = newService();
        long toll = toll();
        long proceeds = propertyService.getSellValue(property(101));

        service.sellProperties(1L, List.of(101));

        assertThat(property(101).hasOwner()).isFalse();
        assertThat(property(101).getBuildingLevel()).isEqualTo(BuildingLevel.LAND);
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(toll - 1 + proceeds - toll);
        assertThat(state().getPlayerState(2L).getMoney()).isEqualTo(GameService.START_MONEY + toll);
    }

    @Test
    void 매각하면_PROPERTIES_SOLD_알림이_나가고_턴이_넘어간다() {
        GameService service = newService();

        GameResult result = service.sellProperties(1L, List.of(101));

        assertThat(result.roomId()).isEqualTo(ROOM);
        assertThat(result.types()).containsExactly(MessageType.PROPERTIES_SOLD);
        PropertiesSoldPayload payload =
                result.payloadsOf(MessageType.PROPERTIES_SOLD, PropertiesSoldPayload.class).get(0);
        assertThat(payload.playerId()).isEqualTo(1L);
        assertThat(payload.propertyIds()).containsExactly(101);
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 건물이_있던_땅은_매각가가_크고_건설_단계도_초기화된다() {
        GameService service = newService();
        property(101).setBuildingLevel(BuildingLevel.HOTEL);   // 투자금 350,000 → 매각가 175,000
        long proceeds = propertyService.getSellValue(property(101));

        service.sellProperties(1L, List.of(101));

        assertThat(proceeds).isEqualTo(175_000);
        assertThat(property(101).getBuildingLevel()).isEqualTo(BuildingLevel.LAND);
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(toll() - 1 + proceeds - toll());
    }

    @Test
    void 여러_땅을_한_번에_매각할_수_있다() {
        GameService service = newService();
        long proceeds = propertyService.getSellValue(property(101)) + propertyService.getSellValue(property(105));

        service.sellProperties(1L, List.of(101, 105));

        assertThat(property(101).hasOwner()).isFalse();
        assertThat(property(105).hasOwner()).isFalse();
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(toll() - 1 + proceeds - toll());
    }

    @Test
    void 팔아도_통행료에_못_미치면_거부되고_상태가_바뀌지_않는다() {
        GameService service = newService();
        property(103).setBuildingLevel(BuildingLevel.HOTEL);   // 통행료 180,000
        state().getPlayerState(1L).setMoney(0);

        assertRejected(() -> service.sellProperties(1L, List.of(101)), ErrorCode.NOT_ENOUGH_SELL);

        assertThat(property(101).isOwnedBy(1L)).isTrue();
        assertThat(state().getPlayerState(1L).getMoney()).isZero();
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_SELL);
        assertThat(state().getCurrentPlayerId()).isEqualTo(1L);
    }

    @Test
    void 목록이_비었으면_거부된다() {
        GameService service = newService();

        assertRejected(() -> service.sellProperties(1L, List.of()), ErrorCode.INVALID_PROPERTY_LIST);
    }

    @Test
    void 땅_번호가_중복되면_거부된다() {
        GameService service = newService();

        assertRejected(() -> service.sellProperties(1L, List.of(101, 101)), ErrorCode.INVALID_PROPERTY_LIST);
        assertThat(property(101).isOwnedBy(1L)).isTrue();
    }

    @Test
    void 목록이_null이면_거부된다() {
        GameService service = newService();

        assertRejected(() -> service.sellProperties(1L, null), ErrorCode.INVALID_PROPERTY_LIST);

        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_SELL);
        assertThat(property(101).isOwnedBy(1L)).isTrue();
    }

    @Test
    void 목록에_null_값이_있으면_거부된다() {
        GameService service = newService();
        List<Integer> withNull = new ArrayList<>();
        withNull.add(101);
        withNull.add(null);

        assertRejected(() -> service.sellProperties(1L, withNull), ErrorCode.INVALID_PROPERTY_LIST);

        assertThat(property(101).isOwnedBy(1L)).isTrue();
        assertThat(state().getCurrentPlayerId()).isEqualTo(1L);
    }

    @Test
    void 없는_땅_번호면_거부된다() {
        GameService service = newService();

        assertRejected(() -> service.sellProperties(1L, List.of(999)), ErrorCode.INVALID_PROPERTY);
    }

    @Test
    void 내_땅이_아니면_거부되고_상태가_바뀌지_않는다() {
        GameService service = newService();

        assertRejected(() -> service.sellProperties(1L, List.of(101, 103)), ErrorCode.NOT_OWNER);

        assertThat(property(101).isOwnedBy(1L)).isTrue();
    }

    @Test
    void 매각을_고를_phase가_아니면_거부된다() {
        GameService service = newService();
        state().setPhase(TurnPhase.AWAITING_ROLL);

        assertRejected(() -> service.sellProperties(1L, List.of(101)), ErrorCode.INVALID_STATE);
    }

    @Test
    void 내_차례가_아니면_거부된다() {
        GameService service = newService();

        assertRejected(() -> service.sellProperties(2L, List.of(101)), ErrorCode.NOT_YOUR_TURN);
    }
}