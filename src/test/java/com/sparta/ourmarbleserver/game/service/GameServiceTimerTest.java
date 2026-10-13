package com.sparta.ourmarbleserver.game.service;

import com.sparta.ourmarbleserver.game.state.GameConfig;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.game.state.InMemoryGameStateRepository;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.property.domain.BuildingLevel;
import com.sparta.ourmarbleserver.scheduler.service.TimerService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상태를 저장하는 때(게임 시작, 요청 처리 끝)에 타이머가 새로 시작되는지 확인한다.
 * 제한 시간을 1초로 짧게 잡고 실제로 기다린다. (시간에 기대는 테스트라서 아주 느린 환경에서는 가끔 실패할 수 있다)
 */
class GameServiceTimerTest {

    private static final String ROOM = "r1";
    private static final GameConfig CONFIG = GameConfig.defaults().withLimitTimeSeconds(1);

    private record Timeout(String roomId, long playerId, int turnNumber, TurnPhase phase) {
    }

    private final GameStateRepository repository = new InMemoryGameStateRepository();
    private final TimerService timer = new TimerService();
    private final BlockingQueue<Timeout> timeouts = new LinkedBlockingQueue<>();

    private GameService newService(int... faces) {
        timer.setHandler((roomId, playerId, turnNumber, phase) ->
                timeouts.add(new Timeout(roomId, playerId, turnNumber, phase)));
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
    void 게임을_시작하면_첫_플레이어의_타이머가_시작된다() throws Exception {
        GameService service = newService();

        start(service);

        assertThat(waitTimeout(2000)).isEqualTo(new Timeout(ROOM, 1L, 1, TurnPhase.AWAITING_ROLL));
    }

    @Test
    void 요청을_처리하면_타이머가_처음부터_다시_시작되고_그_뒤에_기다리는_입력을_알려_준다() throws Exception {
        GameService service = newService(1, 2);   // 주사위 1, 2 → 3번 칸(땅 103) 도착
        start(service);
        Thread.sleep(500);

        service.rollDice(1L);                     // 0.5초에 입력

        assertThat(waitTimeout(700)).isNull();    // 처음 기준 1.2초: 이전 타이머가 남아 있었다면 이미 불렸다
        assertThat(waitTimeout(800)).isEqualTo(new Timeout(ROOM, 1L, 1, TurnPhase.AWAITING_PURCHASE));
    }

    @Test
    void 봇_차례는_제한_시간_대신_3초_뒤에_시간_초과가_난다() throws Exception {
        GameService service = newService();

        service.startGame(ROOM, List.of(1L, 2L), Map.of(), Set.of(1L), GameConfig.defaults().withLimitTimeSeconds(20));

        assertThat(waitTimeout(2000)).isNull();   // 2초: 제한 시간(20초)이 아니라 봇 대기 시간(3초)이다
        assertThat(waitTimeout(2000)).isEqualTo(new Timeout(ROOM, 1L, 1, TurnPhase.AWAITING_ROLL));
    }

    @Test
    void 타이머가_울려도_사람이_가만히_있으면_서버가_대신_진행한다() throws Exception {
        GameService service = newService(1, 2);   // 주사위 1, 2 → 3번 칸(땅 103) 도착
        BlockingQueue<String> results = new LinkedBlockingQueue<>();
        timer.setHandler((roomId, playerId, turnNumber, phase) -> {
            service.rollDice(playerId);   // 타이머 스레드의 서버 대신 진행
            results.add("실행됨");
        });

        start(service);

        assertThat(results.poll(3, TimeUnit.SECONDS)).isEqualTo("실행됨");
        assertThat(repository.findById(ROOM).orElseThrow().getPhase()).isEqualTo(TurnPhase.AWAITING_PURCHASE);
    }

    @Test
    void 타이머가_울린_뒤_사람이_먼저_요청을_보내면_서버_대신_진행은_거부된다() throws Exception {
        GameService service = newService(1, 2);
        BlockingQueue<String> results = new LinkedBlockingQueue<>();
        timer.setHandler((roomId, playerId, turnNumber, phase) -> {
            Thread human = new Thread(() -> {
                try {
                    service.drawCard(playerId);   // 사람의 요청: 카드 칸이 아니라서 거부되지만, 사람이 있다는 뜻이라 AFK가 풀린다
                } catch (GameException ignored) {
                }
            });
            human.start();
            try {
                human.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                service.rollDice(playerId);   // 서버 대신 진행
                results.add("실행됨");
            } catch (GameException e) {
                results.add("거부됨");
            }
        });

        start(service);

        assertThat(results.poll(3, TimeUnit.SECONDS)).isEqualTo("거부됨");
        assertThat(repository.findById(ROOM).orElseThrow().getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);   // 주사위도 안 굴렸다
    }

    @Test
    void 턴이_넘어가면_다음_플레이어의_타이머가_시작된다() throws Exception {
        GameService service = newService(1, 2);
        start(service);
        service.rollDice(1L);

        service.purchaseProperty(1L, 103, false);

        assertThat(waitTimeout(2000)).isEqualTo(new Timeout(ROOM, 2L, 2, TurnPhase.AWAITING_ROLL));
    }

    @Test
    void 게임이_끝나면_타이머가_지워진다() throws Exception {
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