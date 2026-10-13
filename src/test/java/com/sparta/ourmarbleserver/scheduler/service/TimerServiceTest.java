package com.sparta.ourmarbleserver.scheduler.service;

import com.sparta.ourmarbleserver.game.state.TurnPhase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 타이머 테스트. 제한 시간을 1~2초로 짧게 잡고 실제로 기다린다.
 * 시간에 기대는 테스트라서 아주 느린 환경에서는 간격(여유 0.2초)이 모자랄 수 있다.
 */
class TimerServiceTest {

    private record Timeout(String roomId, long playerId, int turnNumber, TurnPhase phase) {
    }

    private final TimerService timer = new TimerService();
    private final BlockingQueue<Timeout> timeouts = new LinkedBlockingQueue<>();

    private Timeout waitTimeout(long millis) throws InterruptedException {
        return timeouts.poll(millis, TimeUnit.MILLISECONDS);
    }

    private void recordTimeouts() {
        timer.setHandler((roomId, playerId, turnNumber, phase) ->
                timeouts.add(new Timeout(roomId, playerId, turnNumber, phase)));
    }

    @Test
    void 시작하고_제한_시간이_지나면_처리기가_불린다() throws Exception {
        recordTimeouts();

        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 1);

        assertThat(waitTimeout(500)).isNull();   // 0.5초: 아직 아니다
        assertThat(waitTimeout(1000)).isEqualTo(new Timeout("r1", 1L, 1, TurnPhase.AWAITING_ROLL));
        assertThat(waitTimeout(1500)).isNull();  // 한 번만 불린다
    }

    @Test
    void 제한_시간_안에_다시_시작하면_처음부터_다시_센다() throws Exception {
        recordTimeouts();
        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 1);
        Thread.sleep(500);

        timer.start("r1", 1L, 1, TurnPhase.AWAITING_PURCHASE, 1);   // 0.5초에 입력이 와서 다시 시작
        assertThat(waitTimeout(700)).isNull();   // 처음 기준 1.2초: 이전 타이머가 남아 있었다면 이미 불렸다

        assertThat(waitTimeout(800)).isEqualTo(new Timeout("r1", 1L, 1, TurnPhase.AWAITING_PURCHASE));
    }

    @Test
    void 취소하면_처리기가_불리지_않는다() throws Exception {
        recordTimeouts();
        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 1);

        timer.cancel("r1");

        assertThat(waitTimeout(1500)).isNull();
    }

    @Test
    void 방마다_따로_돌고_처리기가_예외를_던져도_다음_타이머는_정상이다() throws Exception {
        timer.setHandler((roomId, playerId, turnNumber, phase) -> {
            timeouts.add(new Timeout(roomId, playerId, turnNumber, phase));
            throw new IllegalStateException("처리 실패");
        });

        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 1);
        timer.start("r2", 5L, 3, TurnPhase.AWAITING_BUILD, 2);

        assertThat(waitTimeout(1500)).isEqualTo(new Timeout("r1", 1L, 1, TurnPhase.AWAITING_ROLL));
        assertThat(waitTimeout(1500)).isEqualTo(new Timeout("r2", 5L, 3, TurnPhase.AWAITING_BUILD));
    }

    @Test
    void 시간_초과가_난_플레이어는_다음_입력부터_짧게_기다린다() throws Exception {
        timer.setHandler((roomId, playerId, turnNumber, phase) -> {
            timeouts.add(new Timeout(roomId, playerId, turnNumber, phase));
            if (phase == TurnPhase.AWAITING_ROLL) {   // 대신 진행이 끝나고 GameService가 다음 타이머를 시작한 상황
                timer.start(roomId, playerId, turnNumber, TurnPhase.AWAITING_PURCHASE, 4);
            }
        });

        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 4);

        assertThat(waitTimeout(4500)).isEqualTo(new Timeout("r1", 1L, 1, TurnPhase.AWAITING_ROLL));     // 첫 입력은 제한 시간(4초)
        assertThat(waitTimeout(2500)).isNull();                                                                                  // AFK 대기 3초가 지나기 전
        assertThat(waitTimeout(1000)).isEqualTo(new Timeout("r1", 1L, 1, TurnPhase.AWAITING_PURCHASE)); // 다음은 3초 만에
    }

    @Test
    void 사람이_직접_입력하면_AFK가_풀려서_제한_시간을_다시_기다린다() throws Exception {
        timer.setHandler((roomId, playerId, turnNumber, phase) -> {
            timeouts.add(new Timeout(roomId, playerId, turnNumber, phase));
            if (phase == TurnPhase.AWAITING_ROLL) {
                timer.start(roomId, playerId, turnNumber, TurnPhase.AWAITING_PURCHASE, 2);   // 대신 진행이 시작한 타이머
            }
        });
        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 1);
        assertThat(waitTimeout(1500)).isNotNull();   // 시간 초과: 1번 플레이어가 AFK가 된다

        timer.start("r1", 1L, 1, TurnPhase.AWAITING_PURCHASE, 5);   // 사람이 직접 입력해서 시작된 타이머

        assertThat(waitTimeout(3500)).isNull();   // AFK였다면 1초 만에 울렸다
        assertThat(waitTimeout(2000)).isEqualTo(new Timeout("r1", 1L, 1, TurnPhase.AWAITING_PURCHASE));   // 제한 시간(2초)
    }
}