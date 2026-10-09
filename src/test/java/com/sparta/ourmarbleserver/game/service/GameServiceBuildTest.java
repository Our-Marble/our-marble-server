package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.sparta.ourmarbleserver.property.dto.BuiltPayload;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import tools.jackson.databind.json.JsonMapper;

/**
 * 건설·거절(BUILD) 테스트. 플레이어 1이 3번 칸(땅 103 베이징, 자기 땅)에서 건설을 고르는 상태에서 시작한다.
 * 베이징 건설비: 별장 50,000 / 빌딩 100,000 / 호텔 150,000
 */
class GameServiceBuildTest {

    private static final String ROOM = "r1";
    private static final int PROPERTY_ID = 103;

    private final GameStateRepository repository = new InMemoryGameStateRepository();
    private final JsonMapper mapper = JsonMapper.builder().build();

    private GameService newService() {
        GameDataService data = new GameDataService(mapper);
        GameService service = new GameService(repository, new DiceService(),
                new MoveService(data), new TurnService(data), new EconomyService(), new PropertyService(data), data);
        service.startGame(ROOM, List.of(1L, 2L));

        GameState state = state();
        state.getPlayerState(1L).setPosition(3);
        property(PROPERTY_ID).setOwnerId(1L);
        state.setPhase(TurnPhase.AWAITING_BUILD);
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
    void 건설하면_건설비가_차감되고_한_단계_올라간다() {
        GameService service = newService();

        service.build(1L, PROPERTY_ID, true);

        assertThat(property(PROPERTY_ID).getBuildingLevel()).isEqualTo(BuildingLevel.VILLA);
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY - 50_000);
    }

    @Test
    void 건설비는_올라갈_단계의_비용이다() {
        GameService service = newService();
        property(PROPERTY_ID).setBuildingLevel(BuildingLevel.VILLA);

        service.build(1L, PROPERTY_ID, true);

        assertThat(property(PROPERTY_ID).getBuildingLevel()).isEqualTo(BuildingLevel.BUILDING);
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY - 100_000);
    }

    @Test
    void 건설하면_BUILT_알림이_나가고_턴이_넘어간다() {
        GameService service = newService();

        GameResult result = service.build(1L, PROPERTY_ID, true);

        assertThat(result.roomId()).isEqualTo(ROOM);
        assertThat(result.types()).containsExactly(MessageType.BUILT);
        BuiltPayload payload = result.payloadsOf(MessageType.BUILT, BuiltPayload.class).get(0);
        assertThat(payload.playerId()).isEqualTo(1L);
        assertThat(payload.propertyId()).isEqualTo(PROPERTY_ID);
        assertThat(payload.isAccept()).isTrue();
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 거절하면_상태는_그대로이고_isAccept_false로_알리고_턴이_넘어간다() {
        GameService service = newService();

        GameResult result = service.build(1L, PROPERTY_ID, false);

        assertThat(property(PROPERTY_ID).getBuildingLevel()).isEqualTo(BuildingLevel.LAND);
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY);
        assertThat(result.payloadsOf(MessageType.BUILT, BuiltPayload.class).get(0).isAccept()).isFalse();
        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
    }

    @Test
    void 더블이면_건설_뒤에_같은_플레이어가_다시_굴린다() {
        GameService service = newService();
        state().setDouble(true);

        service.build(1L, PROPERTY_ID, true);

        assertThat(state().getCurrentPlayerId()).isEqualTo(1L);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
    }

    @Test
    void 현금이_부족하면_거부되고_상태가_바뀌지_않는다() {
        GameService service = newService();
        state().getPlayerState(1L).setMoney(49_999);

        assertRejected(() -> service.build(1L, PROPERTY_ID, true), ErrorCode.NOT_ENOUGH_MONEY);

        assertThat(property(PROPERTY_ID).getBuildingLevel()).isEqualTo(BuildingLevel.LAND);
        assertThat(state().getPlayerState(1L).getMoney()).isEqualTo(49_999);
        assertThat(state().getPhase()).isEqualTo(TurnPhase.AWAITING_BUILD);
    }

    @Test
    void 현금이_부족해도_거절은_할_수_있다() {
        GameService service = newService();
        state().getPlayerState(1L).setMoney(0);

        service.build(1L, PROPERTY_ID, false);

        assertThat(state().getCurrentPlayerId()).isEqualTo(2L);
    }

    @Test
    void 이미_호텔이면_거부된다() {
        GameService service = newService();
        property(PROPERTY_ID).setBuildingLevel(BuildingLevel.HOTEL);

        assertRejected(() -> service.build(1L, PROPERTY_ID, true), ErrorCode.MAX_LEVEL);
    }

    @Test
    void 건설_불가_땅이면_거부된다() {
        GameService service = newService();
        state().getPlayerState(1L).setPosition(4);   // 독도 (건설 불가)
        property(104).setOwnerId(1L);

        assertRejected(() -> service.build(1L, 104, true), ErrorCode.CANNOT_BUILD);
    }

    @Test
    void 내_땅이_아니면_거부된다() {
        GameService service = newService();
        property(PROPERTY_ID).setOwnerId(2L);

        assertRejected(() -> service.build(1L, PROPERTY_ID, true), ErrorCode.NOT_OWNER);
    }

    @Test
    void 내_말이_그_땅_위에_없으면_거부된다() {
        GameService service = newService();
        property(104).setOwnerId(1L);

        assertRejected(() -> service.build(1L, 104, true), ErrorCode.INVALID_PROPERTY);
    }

    @Test
    void 없는_땅_번호면_거부된다() {
        GameService service = newService();

        assertRejected(() -> service.build(1L, 999, true), ErrorCode.INVALID_PROPERTY);
    }

    @Test
    void 내_차례가_아니거나_건설을_고를_phase가_아니면_거부된다() {
        GameService service = newService();

        assertRejected(() -> service.build(2L, PROPERTY_ID, true), ErrorCode.NOT_YOUR_TURN);

        state().setPhase(TurnPhase.AWAITING_ROLL);
        assertRejected(() -> service.build(1L, PROPERTY_ID, true), ErrorCode.INVALID_STATE);
    }
}