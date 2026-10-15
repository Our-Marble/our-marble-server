package com.sparta.ourmarbleserver.game.service;

import com.sparta.ourmarbleserver.game.state.GameConfig;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.game.state.InMemoryGameStateRepository;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.GameErrorCode;
import com.sparta.ourmarbleserver.property.domain.BuildingLevel;
import com.sparta.ourmarbleserver.scheduler.repository.InMemoryTimerRepository;
import com.sparta.ourmarbleserver.scheduler.service.TimerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 요청을 검증할 때 마감시간을 확인하고, 상태를 저장하는 때(게임 시작, 요청 처리 끝)에 다음 마감시간이 기록되는지 확인한다.
 * 제한 시간을 1초로 짧게 잡고 실제로 기다린다. (시간에 기대는 테스트라서 아주 느린 환경에서는 가끔 실패할 수 있다)
 * 마감이 지난 방은 0.2초 간격으로 찾으므로 처리기는 마감보다 최대 0.2초 늦게 불린다.
 */
class GameServiceTimerTest {

    private static final String ROOM = "r1";
    private static final GameConfig CONFIG = GameConfig.defaults().withLimitTimeSeconds(1);

    /** 처리기가 불린 때 저장되어 있던 현재 플레이어, 차례, phase */
    private record Timeout(String roomId, long playerId, int turnNumber, TurnPhase phase) {
    }

    private final GameStateRepository repository = new InMemoryGameStateRepository();
    private final TimerService timer = new TimerService(new InMemoryTimerRepository());
    private final BlockingQueue<Timeout> timeouts = new LinkedBlockingQueue<>();

    @AfterEach
    void tearDown() {
        timer.shutdown();
    }

    private GameService newService(int... faces) {
        timer.setHandler(roomId -> {
            GameState state = repository.findById(roomId).orElseThrow();
            timeouts.add(new Timeout(roomId, state.getCurrentPlayerId(), state.getTurnNumber(), state.getPhase()));
            timer.cancel(roomId);   // 대신 진행하지 않고 기록만 한다
        });
        GameDataService data = new GameDataService(JsonMapper.builder().build());
        GameService service = GameTestSupport.newGameService(repository, data, new FixedRandom(faces));
        service.setTimerService(timer);
        return service;
    }

    private GameState start(GameService service) {
        return service.startGame(ROOM, List.of(1L, 2L), Map.of(), Set.of(), CONFIG);
    }

    private Timeout waitTimeout(long millis) throws InterruptedException {
        return timeouts.poll(millis, TimeUnit.MILLISECONDS);
    }

    @Test
    void 게임을_시작하면_첫_플레이어의_마감시간이_기록된다() throws Exception {
        GameService service = newService();

        start(service);

        assertThat(waitTimeout(2000)).isEqualTo(new Timeout(ROOM, 1L, 1, TurnPhase.AWAITING_ROLL));
    }

    @Test
    void 요청을_처리하면_마감시간이_처음부터_다시_기록되고_그_뒤에_기다리는_입력을_대신_진행한다() throws Exception {
        GameService service = newService(1, 2);   // 주사위 1, 2 → 3번 칸(땅 103) 도착
        start(service);
        Thread.sleep(500);

        service.rollDice(1L);                     // 0.5초에 입력

        assertThat(waitTimeout(800)).isNull();    // 처음 기준 1.3초: 이전 마감시간이 남아 있었다면 이미 불렸다
        assertThat(waitTimeout(1000)).isEqualTo(new Timeout(ROOM, 1L, 1, TurnPhase.AWAITING_PURCHASE));
    }

    @Test
    void 봇_차례는_제한_시간_대신_3초_뒤에_대신_진행한다() throws Exception {
        GameService service = newService();

        service.startGame(ROOM, List.of(1L, 2L), Map.of(), Set.of(1L), GameConfig.defaults().withLimitTimeSeconds(20));

        assertThat(waitTimeout(2000)).isNull();   // 2초: 제한 시간(20초)이 아니라 봇 대기 시간(3초)이다
        assertThat(waitTimeout(2000)).isEqualTo(new Timeout(ROOM, 1L, 1, TurnPhase.AWAITING_ROLL));
    }

    @Test
    void 마감시간이_지나면_서버가_대신_진행하고_그_사람은_AFK가_된다() throws Exception {
        GameService service = newService(1, 2);   // 주사위 1, 2 → 3번 칸(땅 103) 도착
        BlockingQueue<String> results = new LinkedBlockingQueue<>();
        timer.setHandler(roomId -> {
            service.rollDice(repository.findById(roomId).orElseThrow().getCurrentPlayerId());   // 서버 대신 진행
            results.add("실행됨");
        });

        start(service);

        assertThat(results.poll(3, TimeUnit.SECONDS)).isEqualTo("실행됨");
        assertThat(repository.findById(ROOM).orElseThrow().getPhase()).isEqualTo(TurnPhase.AWAITING_PURCHASE);
        assertThat(timer.isAfk(ROOM, 1L)).isTrue();
    }

    @Test
    void 대신_진행이_시작된_뒤_도착한_사람의_요청은_거부된다() throws Exception {
        GameService service = newService(1, 2);
        BlockingQueue<Object> results = new LinkedBlockingQueue<>();
        timer.setHandler(roomId -> {
            Thread human = new Thread(() -> {
                try {
                    service.rollDice(1L);   // 늦게 도착한 사람의 요청 (다른 서버에서 처리된다고 생각해도 같다)
                    results.add("사람 요청 처리됨");
                } catch (GameException e) {
                    results.add(e);
                }
            });
            human.start();
            try {
                human.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            service.rollDice(1L);   // 서버 대신 진행
            results.add("대신 진행됨");
        });

        start(service);

        Object human = results.poll(3, TimeUnit.SECONDS);
        assertThat(human).isInstanceOf(GameException.class);
        assertThat(((GameException) human).code()).isEqualTo(GameErrorCode.INVALID_STATE);
        assertThat(results.poll(3, TimeUnit.SECONDS)).isEqualTo("대신 진행됨");
        assertThat(repository.findById(ROOM).orElseThrow().getPhase()).isEqualTo(TurnPhase.AWAITING_PURCHASE);
    }

    @Test
    void 검증에서_거부된_요청은_마감시간을_바꾸지_않는다() throws Exception {
        GameService service = newService(1, 2);
        start(service);
        Thread.sleep(500);

        assertThatThrownBy(() -> service.drawCard(1L)).isInstanceOf(GameException.class);   // 카드 칸이 아니다

        assertThat(waitTimeout(1200)).isEqualTo(new Timeout(ROOM, 1L, 1, TurnPhase.AWAITING_ROLL));   // 원래 마감(1초)
    }

    @Test
    void 처리_중에_예외가_나면_리스를_되돌려_원래_마감시간에_대신_진행한다() throws Exception {
        GameService service = newService(1, 2);   // 주사위 1, 2 → 3번 칸(땅 103) 도착
        GameState state = start(service);
        service.rollDice(1L);
        state.getPlayerState(1L).setMoney(0);

        assertThatThrownBy(() -> service.purchaseProperty(1L, 103, true))   // 검증을 통과한 뒤 돈이 모자라 실패
                .isInstanceOf(GameException.class);

        // 리스(10초)가 남아 있었다면 10초 뒤에나 불린다
        assertThat(waitTimeout(1500)).isEqualTo(new Timeout(ROOM, 1L, 1, TurnPhase.AWAITING_PURCHASE));
    }

    @Test
    void 턴이_넘어가면_다음_플레이어의_마감시간이_기록된다() throws Exception {
        GameService service = newService(1, 2);
        start(service);
        service.rollDice(1L);

        service.purchaseProperty(1L, 103, false);

        assertThat(waitTimeout(2000)).isEqualTo(new Timeout(ROOM, 2L, 2, TurnPhase.AWAITING_ROLL));
    }

    @Test
    void 게임이_끝나면_마감시간이_지워진다() throws Exception {
        GameService service = newService(1, 2);
        GameState state = start(service);
        state.getPropertyState(103).orElseThrow().setOwnerId(2L);   // 파산 상황: 통행료를 낼 수 없다
        state.getPropertyState(103).orElseThrow().setBuildingLevel(BuildingLevel.HOTEL);
        state.getPlayerState(1L).setMoney(10_000);

        service.rollDice(1L);

        assertThat(state.isGameOver()).isTrue();
        assertThat(waitTimeout(1500)).isNull();
    }

    @Test
    void 타이머를_넣지_않아도_게임은_동작한다() {
        GameDataService data = new GameDataService(JsonMapper.builder().build());
        GameService service = GameTestSupport.newGameService(repository, data, new FixedRandom(1, 2));

        service.startGame(ROOM, List.of(1L, 2L));
        service.rollDice(1L);

        assertThat(repository.findById(ROOM).orElseThrow().getPhase()).isEqualTo(TurnPhase.AWAITING_PURCHASE);
    }
}
