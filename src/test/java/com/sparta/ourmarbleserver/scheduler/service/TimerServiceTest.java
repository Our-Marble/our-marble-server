package com.sparta.ourmarbleserver.scheduler.service;

import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.scheduler.repository.InMemoryTimerRepository;
import com.sparta.ourmarbleserver.scheduler.repository.TimerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 타이머 테스트. 저장소는 메모리 구현을 쓰고, 제한 시간을 1~4초로 짧게 잡고 실제로 기다린다.
 * 마감이 지난 방은 0.2초 간격으로 찾으므로 처리기는 마감보다 최대 0.2초 늦게 불린다.
 * 시간에 기대는 테스트라서 아주 느린 환경에서는 간격이 모자랄 수 있다.
 */
class TimerServiceTest {

    private static final long LEASE_MILLIS = 1_500;

    private final TimerRepository repository = new InMemoryTimerRepository();
    private final TimerService timer = new TimerService(repository, LEASE_MILLIS);
    private final BlockingQueue<String> timeouts = new LinkedBlockingQueue<>();

    @AfterEach
    void tearDown() {
        timer.shutdown();
    }

    private String waitTimeout(long millis) throws InterruptedException {
        return timeouts.poll(millis, TimeUnit.MILLISECONDS);
    }

    private void recordTimeouts() {
        timer.setHandler(timeouts::add);
    }

    /** 다른 스레드(사람의 요청)에서 리스를 잡아 본다. */
    private boolean acquireOnOtherThread(String roomId, long playerId) throws InterruptedException {
        AtomicBoolean acquired = new AtomicBoolean();
        Thread human = new Thread(() -> acquired.set(timer.tryAcquire(roomId, playerId, false)));
        human.start();
        human.join();
        return acquired.get();
    }

    @Test
    void 마감시간이_지나면_처리기가_한_번_불린다() throws Exception {
        recordTimeouts();

        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 1);

        assertThat(waitTimeout(700)).isNull();       // 0.7초: 아직 아니다
        assertThat(waitTimeout(1000)).isEqualTo("r1");
        assertThat(waitTimeout(1500)).isNull();      // 한 번만 불린다
    }

    @Test
    void 마감_전에_다시_기록하면_처음부터_다시_센다() throws Exception {
        recordTimeouts();
        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 1);
        Thread.sleep(500);

        timer.start("r1", 1L, 1, TurnPhase.AWAITING_PURCHASE, 1);   // 0.5초에 입력이 와서 다시 기록
        assertThat(waitTimeout(800)).isNull();      // 처음 기준 1.3초: 이전 마감시간이 남아 있었다면 이미 불렸다

        assertThat(waitTimeout(1000)).isEqualTo("r1");
    }

    @Test
    void 취소하면_처리기가_불리지_않는다() throws Exception {
        recordTimeouts();
        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 1);

        timer.cancel("r1");

        assertThat(waitTimeout(1500)).isNull();
    }

    @Test
    void 방마다_따로_돌고_처리기가_예외를_던져도_다른_방은_정상이다() throws Exception {
        timer.setHandler(roomId -> {
            timeouts.add(roomId);
            throw new IllegalStateException("처리 실패");
        });

        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 1);
        timer.start("r2", 5L, 3, TurnPhase.AWAITING_BUILD, 2);

        assertThat(waitTimeout(1500)).isEqualTo("r1");
        assertThat(waitTimeout(1500)).isEqualTo("r2");
    }

    @Test
    void 대신_진행이_실패하면_리스_시간이_지난_뒤_다시_시도한다() throws Exception {
        timer.setHandler(roomId -> {
            timeouts.add(roomId);
            throw new IllegalStateException("처리 실패");   // 마감시간을 새로 기록하지 못했다
        });

        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 1);

        assertThat(waitTimeout(1500)).isEqualTo("r1");
        assertThat(waitTimeout(1000)).isNull();               // 바로 다시 하지 않는다
        assertThat(waitTimeout(1500)).isEqualTo("r1");        // 리스 시간(1.5초) 뒤 다시
    }

    @Test
    void 서버가_여러_대여도_대신_진행은_한_곳에서_한_번만_한다() throws Exception {
        TimerService other = new TimerService(repository, LEASE_MILLIS);   // 같은 저장소(Redis)를 보는 다른 서버
        AtomicInteger calls = new AtomicInteger();
        timer.setHandler(roomId -> {
            calls.incrementAndGet();
            timer.start(roomId, 1L, 1, TurnPhase.AWAITING_PURCHASE, 60);
        });
        other.setHandler(roomId -> {
            calls.incrementAndGet();
            other.start(roomId, 1L, 1, TurnPhase.AWAITING_PURCHASE, 60);
        });
        try {
            timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 1);
            Thread.sleep(2500);

            assertThat(calls.get()).isEqualTo(1);
        } finally {
            other.shutdown();
        }
    }

    // ===== 사람의 요청 =====

    @Test
    void 사람의_요청을_처리하는_동안에는_마감시간이_늘어나_대신_진행되지_않는다() throws Exception {
        recordTimeouts();
        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 1);

        assertThat(timer.tryAcquire("r1", 1L, false)).isTrue();   // 리스를 잡고 처리 중

        assertThat(waitTimeout(1200)).isNull();                    // 원래 마감(1초)이 지나도 대신 진행하지 않는다
        timer.start("r1", 1L, 1, TurnPhase.AWAITING_PURCHASE, 1);  // 처리를 마치고 다음 마감시간을 기록
        assertThat(waitTimeout(1500)).isEqualTo("r1");
    }

    @Test
    void 사람의_요청이_실패하면_마감시간이_원래대로_돌아간다() throws Exception {
        recordTimeouts();
        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 2);
        assertThat(timer.tryAcquire("r1", 1L, false)).isTrue();

        timer.releaseHeld();   // 처리 중 예외

        assertThat(waitTimeout(1500)).isNull();
        assertThat(waitTimeout(1000)).isEqualTo("r1");   // 원래 마감(2초)에 불린다. 리스 시간만큼 늦어지지 않는다
    }

    @Test
    void 처리_중인_요청이_있으면_다른_요청은_거부된다() throws Exception {
        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 5);
        assertThat(timer.tryAcquire("r1", 1L, false)).isTrue();

        assertThat(acquireOnOtherThread("r1", 1L)).isFalse();   // 같은 방의 다른 요청(다른 서버일 수도 있다)

        timer.start("r1", 1L, 1, TurnPhase.AWAITING_PURCHASE, 5);   // 앞 요청이 끝났다
        assertThat(acquireOnOtherThread("r1", 1L)).isTrue();
    }

    @Test
    void 처리_중에_서버가_죽으면_리스_시간이_지난_뒤_대신_진행된다() throws Exception {
        recordTimeouts();
        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 60);
        assertThat(acquireOnOtherThread("r1", 1L)).isTrue();   // 이 스레드는 리스를 놓지 않고 끝났다 (서버가 죽음)

        assertThat(waitTimeout(LEASE_MILLIS + 700)).isEqualTo("r1");
    }

    @Test
    void 대신_진행이_리스를_잡은_뒤_도착한_사람의_요청은_거부된다() throws Exception {
        BlockingQueue<Boolean> acquired = new LinkedBlockingQueue<>();
        timer.setHandler(roomId -> {
            try {
                acquired.add(acquireOnOtherThread(roomId, 1L));   // 사람의 요청이 늦게 도착했다
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 1);

        assertThat(acquired.poll(2, TimeUnit.SECONDS)).isFalse();
    }

    // ===== AFK =====

    @Test
    void 대신_진행된_사람은_AFK가_되어_다음_입력부터_짧게_기다린다() throws Exception {
        timer.setHandler(roomId -> {
            timer.tryAcquire(roomId, 1L, false);                              // 대신 진행이 GameService.validate를 거친다
            timer.start(roomId, 1L, 1, TurnPhase.AWAITING_PURCHASE, 4);       // 대신 진행을 마치고 다음 마감시간 기록
            timeouts.add(roomId);
        });

        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 4);

        assertThat(waitTimeout(4500)).isEqualTo("r1");   // 첫 입력은 제한 시간(4초)
        assertThat(timer.isAfk("r1", 1L)).isTrue();
        assertThat(waitTimeout(5500)).isNull();          // AFK 대기 6초가 지나기 전
        assertThat(waitTimeout(1000)).isEqualTo("r1");   // 다음은 6초 만에
    }

    @Test
    void 사람이_요청을_보내면_AFK가_풀려서_제한_시간을_다시_기다린다() throws Exception {
        CountDownLatch timedOut = new CountDownLatch(1);
        timer.setHandler(roomId -> {
            timer.tryAcquire(roomId, 1L, false);
            timer.start(roomId, 1L, 1, TurnPhase.AWAITING_PURCHASE, 60);
            timedOut.countDown();
        });
        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 1);
        assertThat(timedOut.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(timer.isAfk("r1", 1L)).isTrue();

        timer.release("r1", 1L);   // 사람이 요청을 보냈다 (거부되더라도 AFK는 풀린다)

        assertThat(timer.isAfk("r1", 1L)).isFalse();
    }

    @Test
    void 대신_진행_중에_부른_AFK_해제는_무시된다() throws Exception {
        BlockingQueue<Boolean> afk = new LinkedBlockingQueue<>();
        timer.setHandler(roomId -> {
            timer.tryAcquire(roomId, 1L, false);
            timer.release(roomId, 1L);   // 대신 진행이 부른 것이라 무시된다
            afk.add(timer.isAfk(roomId, 1L));
        });

        timer.start("r1", 1L, 1, TurnPhase.AWAITING_ROLL, 1);

        assertThat(afk.poll(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void 봇은_대신_진행되어도_AFK로_표시되지_않는다() throws Exception {
        BlockingQueue<Boolean> afk = new LinkedBlockingQueue<>();
        timer.setHandler(roomId -> {
            timer.tryAcquire(roomId, -1L, true);
            afk.add(timer.isAfk(roomId, -1L));
        });

        timer.startBot("r1", -1L, 1, TurnPhase.AWAITING_ROLL, 1);

        assertThat(afk.poll(2, TimeUnit.SECONDS)).isFalse();
    }

    @Test
    void 취소하면_AFK_기록도_지워진다() {
        repository.addAfk("r1", 1L);

        timer.cancel("r1");

        assertThat(timer.isAfk("r1", 1L)).isFalse();
    }
}
