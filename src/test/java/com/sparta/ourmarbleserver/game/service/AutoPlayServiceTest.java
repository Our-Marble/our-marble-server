package com.sparta.ourmarbleserver.game.service;

import com.sparta.ourmarbleserver.bot.service.BasicBotStrategy;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.game.state.InMemoryGameStateRepository;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.websocket.WebSocketMessageSender;
import com.sparta.ourmarbleserver.property.domain.BuildingLevel;
import com.sparta.ourmarbleserver.property.service.PropertyService;
import com.sparta.ourmarbleserver.scheduler.service.AutoPlayService;
import com.sparta.ourmarbleserver.scheduler.service.TimerService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 시간 초과 대신 진행 테스트. 타이머를 기다리지 않고 onTimeout을 직접 부른다.
 * 알림은 가짜 sender(Mockito mock)로 받아서 방 전원에게 보냈는지만 확인한다.
 * 플레이어 1, 2 중 1번 차례로 시작하고, 3번 칸은 땅 103, 1번 칸은 땅 101, 24번 칸은 세계여행이다.
 */
class AutoPlayServiceTest {

    private static final String ROOM = "r1";

    private final GameStateRepository repository = new InMemoryGameStateRepository();
    private final WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
    private AutoPlayService autoPlay;
    private GameService service;

    private GameState start(int... faces) {
        GameDataService data = new GameDataService(JsonMapper.builder().build());
        service = GameTestSupport.newGameService(repository, data, new FixedRandom(faces));
        PropertyService propertyService = new PropertyService(data);
        autoPlay = new AutoPlayService(service, repository, data, propertyService, new BasicBotStrategy(data, propertyService), sender, new TimerService());
        return service.startGame(ROOM, List.of(1L, 2L));
    }

    /** 1번 플레이어가 position 칸에서 phase의 입력을 기다리는 상태로 만든다. */
    private void waitAt(GameState state, int position, TurnPhase phase) {
        state.getPlayerState(1L).setPosition(position);
        state.setPhase(phase);
    }

    private void own(GameState state, int propertyId, long ownerId, BuildingLevel level) {
        state.getPropertyState(propertyId).orElseThrow().setOwnerId(ownerId);
        state.getPropertyState(propertyId).orElseThrow().setBuildingLevel(level);
    }

    @Test
    void 주사위_대기면_주사위를_굴린다() {
        GameState state = start(1, 2);   // 주사위 1, 2 → 3번 칸(땅 103) 도착

        autoPlay.onTimeout(ROOM, 1L, 1, TurnPhase.AWAITING_ROLL);

        assertThat(state.getPlayerState(1L).getPosition()).isEqualTo(3);
        assertThat(state.getPhase()).isEqualTo(TurnPhase.AWAITING_PURCHASE);
        verify(sender).sendToRoom(eq(ROOM), eq(MessageType.DICE_ROLLED), any());
    }

    @Test
    void 구매_대기면_거절하고_턴이_넘어간다() {
        GameState state = start();
        waitAt(state, 3, TurnPhase.AWAITING_PURCHASE);

        autoPlay.onTimeout(ROOM, 1L, 1, TurnPhase.AWAITING_PURCHASE);

        assertThat(state.getPropertyState(103).orElseThrow().hasOwner()).isFalse();
        assertThat(state.getCurrentPlayerId()).isEqualTo(2L);
        verify(sender).sendToRoom(eq(ROOM), eq(MessageType.PROPERTY_PURCHASED), any());
    }

    @Test
    void 건설_대기면_거절하고_턴이_넘어간다() {
        GameState state = start();
        state.getPropertyState(101).orElseThrow().setOwnerId(1L);
        waitAt(state, 1, TurnPhase.AWAITING_BUILD);

        autoPlay.onTimeout(ROOM, 1L, 1, TurnPhase.AWAITING_BUILD);

        assertThat(state.getPropertyState(101).orElseThrow().getBuildingLevel()).isEqualTo(BuildingLevel.LAND);
        assertThat(state.getCurrentPlayerId()).isEqualTo(2L);
        verify(sender).sendToRoom(eq(ROOM), eq(MessageType.BUILT), any());
    }

    @Test
    void 인수_대기면_거절하고_턴이_넘어간다() {
        GameState state = start();
        state.getPropertyState(101).orElseThrow().setOwnerId(2L);
        waitAt(state, 1, TurnPhase.AWAITING_ACQUIRE);

        autoPlay.onTimeout(ROOM, 1L, 1, TurnPhase.AWAITING_ACQUIRE);

        assertThat(state.getPropertyState(101).orElseThrow().isOwnedBy(2L)).isTrue();   // 주인이 그대로다
        assertThat(state.getCurrentPlayerId()).isEqualTo(2L);
        verify(sender).sendToRoom(eq(ROOM), eq(MessageType.PROPERTY_ACQUIRED), any());
    }

    @Test
    void 매각_대기면_싼_땅부터_통행료를_낼_만큼만_판다() {
        GameState state = start();
        state.getPropertyState(103).orElseThrow().setOwnerId(2L);                  // 통행료 60,000 (빌딩)
        state.getPropertyState(103).orElseThrow().setBuildingLevel(BuildingLevel.BUILDING);
        state.getPropertyState(101).orElseThrow().setOwnerId(1L);                  // 매각가 25,000
        state.getPropertyState(105).orElseThrow().setOwnerId(1L);                  // 매각가 25,000
        state.getPropertyState(106).orElseThrow().setOwnerId(1L);                  // 매각가 50,000
        state.getPlayerState(1L).setMoney(10_000);
        waitAt(state, 3, TurnPhase.AWAITING_SELL);

        autoPlay.onTimeout(ROOM, 1L, 1, TurnPhase.AWAITING_SELL);

        assertThat(state.getPropertyState(101).orElseThrow().hasOwner()).isFalse();        // 싼 땅 둘을 판다
        assertThat(state.getPropertyState(105).orElseThrow().hasOwner()).isFalse();
        assertThat(state.getPropertyState(106).orElseThrow().isOwnedBy(1L)).isTrue();       // 나머지는 지킨다
        assertThat(state.getPlayerState(1L).getMoney()).isZero();                           // 10,000 + 50,000 - 60,000
        assertThat(state.getPlayerState(2L).getMoney()).isEqualTo(500_000 + 60_000);
        verify(sender).sendToRoom(eq(ROOM), eq(MessageType.PROPERTIES_SOLD), any());
    }

    @Test
    void 세계여행_대기면_출발_칸으로_간다() {
        GameState state = start();
        waitAt(state, 24, TurnPhase.AWAITING_DESTINATION);

        autoPlay.onTimeout(ROOM, 1L, 1, TurnPhase.AWAITING_DESTINATION);

        assertThat(state.getPlayerState(1L).getPosition()).isZero();
        verify(sender).sendToRoom(eq(ROOM), eq(MessageType.DESTINATION_CHOSEN), any());
    }

    @Test
    void 카드_대기면_카드를_뽑는다() {
        GameState state = start(2);   // 카드 목록의 2번째(돈 받기 200)
        waitAt(state, 2, TurnPhase.AWAITING_DRAW_CARD);

        autoPlay.onTimeout(ROOM, 1L, 1, TurnPhase.AWAITING_DRAW_CARD);

        assertThat(state.getPlayerState(1L).getMoney()).isEqualTo(500_000 + 200);
        verify(sender).sendToRoom(eq(ROOM), eq(MessageType.CARD_DRAWN), any());
    }

    @Test
    void 그_사이_상태가_바뀌었으면_대신_진행하지_않는다() {
        GameState state = start();   // 아직 주사위 대기인데 구매 대기였다고 알려 온 경우

        assertThatThrownBy(() -> autoPlay.onTimeout(ROOM, 1L, 1, TurnPhase.AWAITING_PURCHASE))
                .isInstanceOf(GameException.class);

        assertThat(state.getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
        verifyNoInteractions(sender);
    }

    // ===== 봇: 사람과 달리 전략이 판단한다 =====

    /** 1번 플레이어를 봇으로 만든다. */
    private void makeBot(GameState state) {
        state.getPlayerState(1L).setBot(true);
    }

    @Test
    void 봇은_좋은_땅이면_구매한다() {
        GameState state = start();
        makeBot(state);
        waitAt(state, 4, TurnPhase.AWAITING_PURCHASE);   // 독도(수익 비율 1.5), 땅값 20만

        autoPlay.onTimeout(ROOM, 1L, 1, TurnPhase.AWAITING_PURCHASE);

        assertThat(state.getPropertyState(104).orElseThrow().isOwnedBy(1L)).isTrue();
        assertThat(state.getPlayerState(1L).getMoney()).isEqualTo(300_000);
        verify(sender).sendToRoom(eq(ROOM), eq(MessageType.PROPERTY_PURCHASED), any());
    }

    @Test
    void 봇은_현금이_모자라면_구매를_거절한다() {
        GameState state = start();
        makeBot(state);
        state.getPlayerState(1L).setMoney(150_000);
        waitAt(state, 4, TurnPhase.AWAITING_PURCHASE);

        autoPlay.onTimeout(ROOM, 1L, 1, TurnPhase.AWAITING_PURCHASE);

        assertThat(state.getPropertyState(104).orElseThrow().hasOwner()).isFalse();
        assertThat(state.getCurrentPlayerId()).isEqualTo(2L);
    }

    @Test
    void 봇은_회수가_빠른_땅에는_건설한다() {
        GameState state = start();
        makeBot(state);
        own(state, 131, 1L, BuildingLevel.LAND);   // 서울 별장 15만: 통행료 +12.8만
        waitAt(state, 31, TurnPhase.AWAITING_BUILD);

        autoPlay.onTimeout(ROOM, 1L, 1, TurnPhase.AWAITING_BUILD);

        assertThat(state.getPropertyState(131).orElseThrow().getBuildingLevel()).isEqualTo(BuildingLevel.VILLA);
        assertThat(state.getPlayerState(1L).getMoney()).isEqualTo(350_000);
    }

    @Test
    void 봇은_건설_불가_땅에서는_전략에_묻지_않고_거절한다() {
        GameState state = start();
        makeBot(state);
        own(state, 104, 1L, BuildingLevel.LAND);   // 독도: 건설 불가
        waitAt(state, 4, TurnPhase.AWAITING_BUILD);

        autoPlay.onTimeout(ROOM, 1L, 1, TurnPhase.AWAITING_BUILD);

        assertThat(state.getPropertyState(104).orElseThrow().getBuildingLevel()).isEqualTo(BuildingLevel.LAND);
        assertThat(state.getCurrentPlayerId()).isEqualTo(2L);
    }

    @Test
    void 봇은_호텔에서는_전략에_묻지_않고_거절한다() {
        GameState state = start();
        makeBot(state);
        own(state, 131, 1L, BuildingLevel.HOTEL);
        waitAt(state, 31, TurnPhase.AWAITING_BUILD);

        autoPlay.onTimeout(ROOM, 1L, 1, TurnPhase.AWAITING_BUILD);

        assertThat(state.getPropertyState(131).orElseThrow().getBuildingLevel()).isEqualTo(BuildingLevel.HOTEL);
        assertThat(state.getCurrentPlayerId()).isEqualTo(2L);
    }

    @Test
    void 봇은_통행료로_빨리_회수할_땅이면_인수한다() {
        GameState state = start();
        makeBot(state);
        own(state, 104, 2L, BuildingLevel.LAND);   // 독도: 통행료 30만, 인수가 40만
        state.getPlayerState(1L).setMoney(1_000_000);
        waitAt(state, 4, TurnPhase.AWAITING_ACQUIRE);

        autoPlay.onTimeout(ROOM, 1L, 1, TurnPhase.AWAITING_ACQUIRE);

        assertThat(state.getPropertyState(104).orElseThrow().isOwnedBy(1L)).isTrue();
        assertThat(state.getPlayerState(1L).getMoney()).isEqualTo(600_000);
    }

    @Test
    void 봇은_매각할_땅을_전략이_고른다() {
        GameState state = start();
        makeBot(state);
        own(state, 103, 2L, BuildingLevel.BUILDING);   // 통행료 60,000
        own(state, 101, 1L, BuildingLevel.LAND);       // 매각가 25,000
        own(state, 106, 1L, BuildingLevel.LAND);       // 매각가 50,000
        own(state, 131, 1L, BuildingLevel.LAND);       // 매각가 175,000
        state.getPlayerState(1L).setMoney(10_000);
        waitAt(state, 3, TurnPhase.AWAITING_SELL);

        autoPlay.onTimeout(ROOM, 1L, 1, TurnPhase.AWAITING_SELL);

        // 사람(AFK)이라면 싼 땅부터 101, 106을 팔지만, 봇은 통행료 손실이 적은 서울 하나만 판다
        assertThat(state.getPropertyState(131).orElseThrow().hasOwner()).isFalse();
        assertThat(state.getPropertyState(101).orElseThrow().isOwnedBy(1L)).isTrue();
        assertThat(state.getPropertyState(106).orElseThrow().isOwnedBy(1L)).isTrue();
        assertThat(state.getPlayerState(1L).getMoney()).isEqualTo(10_000 + 175_000 - 60_000);
    }

    @Test
    void 봇은_세계여행_목적지를_전략이_고른다() {
        GameState state = start();
        makeBot(state);
        waitAt(state, 24, TurnPhase.AWAITING_DESTINATION);

        autoPlay.onTimeout(ROOM, 1L, 1, TurnPhase.AWAITING_DESTINATION);

        assertThat(state.getPlayerState(1L).getPosition()).isEqualTo(4);   // 수익 비율이 가장 높은 빈 땅 독도
        assertThat(state.getPhase()).isEqualTo(TurnPhase.AWAITING_PURCHASE);
    }

    @Test
    void 봇만_있는_게임은_끝까지_진행된다() {
        GameDataService data = new GameDataService(JsonMapper.builder().build());
        service = GameTestSupport.newGameService(repository, data, new Random(7));
        PropertyService propertyService = new PropertyService(data);
        autoPlay = new AutoPlayService(service, repository, data, propertyService, new BasicBotStrategy(data, propertyService), sender, new TimerService());
        GameState state = service.startGame(ROOM, List.of(1L, 2L, 3L), Map.of(), Set.of(1L, 2L, 3L));

        for (int i = 0; i < 10_000 && !state.isGameOver(); i++) {
            autoPlay.onTimeout(ROOM, state.getCurrentPlayerId(), state.getTurnNumber(), state.getPhase());
        }

        assertThat(state.isGameOver()).isTrue();
    }
}