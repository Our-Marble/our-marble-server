package com.sparta.ourmarbleserver.scheduler.service;

import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.PlayerState;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.scheduler.repository.TimerRepository;
import jakarta.annotation.PreDestroy;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 방마다 '다음 요청 수신 마감시간'을 저장소(Redis)에 기록하고, 마감이 지난 방은 서버가 대신 진행하게 한다.
 * 타이머를 메모리에 두지 않으므로 서버가 여러 대여도, 한 대가 죽어도 마감시간이 사라지지 않는다.
 *
 * 요청 하나의 흐름 (GameService)
 * 1. validate → tryAcquire: 마감 전이고 처리 중인 요청이 없으면 리스를 잡고 마감을 '지금 + 10초'로 늘린다.
 *    (처리 중에 서버가 죽으면 10초 뒤 다른 서버가 대신 진행한다)
 * 2. 상태 저장 → saveAndSchedule: 상태 저장, 리스 해제, 다음 마감시간 기록을 한 번에(Redis Lua 하나로) 한다. (게임이 끝났으면 타이머를 지운다)
 *    그 사이 리스를 다른 서버가 가져갔으면 상태도 저장하지 않는다.
 * 3. 중간에 예외가 나면 releaseHeld: 리스를 풀고 마감시간을 원래대로 되돌린다.
 * 잡은 리스는 요청을 처리하는 스레드의 ThreadLocal에 들고 있다가 2나 3에서 놓는다.
 *
 * 대신 진행
 * 모든 서버가 짧은 간격으로 마감이 지난 방을 찾고, 그 방의 리스를 먼저 잡은 한 서버만 Handler를 부른다.
 * 대신 진행 스레드에서 GameService가 부르는 tryAcquire는 이미 잡은 리스를 그대로 쓰고, 사람이면 AFK로 표시한다.
 * AFK인 사람은 다음 입력부터 제한 시간 대신 AFK_DELAY_SECONDS만 기다린다. 사람이 요청을 보내면(거부되더라도) AFK가 풀린다.
 */
@Slf4j
@Component
public class TimerService {

    public interface Handler {
        /** 마감시간이 지난 방. 지금 저장된 상태의 현재 플레이어와 phase를 대신 진행한다. */
        void onTimeout(String roomId);
    }

    /** 시간 초과가 난(AFK) 사람은 제한 시간 대신 이 시간만 기다린 뒤 바로 대신 진행한다. */
    static final int AFK_DELAY_SECONDS = 6;

    /** 요청을 처리하는 동안 늘려 두는 마감시간. 처리 중에 서버가 죽으면 이 시간 뒤에 대신 진행된다. */
    static final long DEFAULT_LEASE_MILLIS = 10_000;

    /** 마감이 지난 방을 찾는 간격 */
    private static final long POLL_INTERVAL_MILLIS = 200;

    /** 한 번에 꺼내 볼 방 수 */
    private static final int POLL_BATCH = 50;

    /** 이 스레드가 잡고 있는 리스. auto면 대신 진행 스레드다. */
    private record Lease(String roomId, String token, long previousDeadline, boolean auto) {
    }

    private static final ThreadLocal<Lease> HELD = new ThreadLocal<>();

    private final TimerRepository repository;
    private final long leaseMillis;
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(daemon("timer-poller"));
    private final ExecutorService workers = Executors.newFixedThreadPool(2, daemon("timer-auto-play"));

    /** 대신 진행할 곳. 등록되기 전(서버가 뜨는 중)에는 마감이 지난 방을 건드리지 않는다. */
    @Setter
    private volatile Handler handler;

    @Autowired
    public TimerService(TimerRepository repository) {
        this(repository, DEFAULT_LEASE_MILLIS);
    }

    /** 테스트에서 리스 시간을 짧게 잡을 때 쓴다. */
    TimerService(TimerRepository repository, long leaseMillis) {
        this.repository = repository;
        this.leaseMillis = leaseMillis;
        poller.scheduleWithFixedDelay(this::poll, POLL_INTERVAL_MILLIS, POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        poller.shutdownNow();
        workers.shutdownNow();
    }

    // ===== 상태 저장과 마감시간 기록 (GameService가 부른다) =====

    /**
     * 게임 상태를 저장하면서, 같은 단위로 이 스레드가 잡은 리스를 풀고 다음 요청 수신 마감시간을 기록한다.
     * 다음 마감: 게임이 끝났으면 타이머 삭제, 봇 차례면 botDelaySeconds, AFK인 사람이면 AFK_DELAY_SECONDS, 아니면 제한 시간.
     * @return 저장했으면 true. 리스를 다른 쪽이 가져갔으면 아무것도 저장하지 않고 false.
     */
    public boolean saveAndSchedule(GameState state, int botDelaySeconds) {
        String roomId = state.getRoomId();
        Lease held = HELD.get();
        String token = held != null && held.roomId().equals(roomId) ? held.token() : null;
        long delayMillis = state.isGameOver() ? -1 : nextWaitSeconds(state, botDelaySeconds) * 1000L;

        boolean saved = repository.saveAndSchedule(state, delayMillis, token);
        clearHeld(roomId);
        if (!saved) {
            // 처리하는 데 리스 시간보다 오래 걸려서 그 사이 다른 서버가 대신 진행을 시작했다. 그쪽 결과를 덮어쓰지 않는다.
            log.warn("리스를 잃어 상태를 저장하지 않았습니다. roomId={}", roomId);
        }
        return saved;
    }

    private int nextWaitSeconds(GameState state, int botDelaySeconds) {
        PlayerState current = state.currentPlayer();
        if (current.isBot()) {
            return botDelaySeconds;
        }
        return repository.isAfk(state.getRoomId(), current.getPlayerId())
                ? AFK_DELAY_SECONDS : state.getConfig().limitTimeSeconds();
    }

    // ===== 마감시간만 기록 (상태 저장 없이) =====

    /** 사람 차례의 마감시간만 기록한다. AFK인 사람은 제한 시간 대신 짧게 기다린다. (turnNumber, phase는 기록하지 않는다: 대신 진행은 그때 저장된 상태를 본다) */
    public void start(String roomId, long playerId, int turnNumber, TurnPhase phase, int limitSeconds) {
        int seconds = repository.isAfk(roomId, playerId) ? AFK_DELAY_SECONDS : limitSeconds;
        schedule(roomId, seconds);
    }

    /** 봇 차례의 마감시간만 기록한다. 봇은 항상 delaySeconds 뒤에 대신 진행한다. */
    public void startBot(String roomId, long playerId, int turnNumber, TurnPhase phase, int delaySeconds) {
        schedule(roomId, delaySeconds);
    }

    /** 게임이 끝났다. 마감시간, 리스, AFK 기록을 모두 지운다. */
    public void cancel(String roomId) {
        repository.remove(roomId);
        clearHeld(roomId);
    }

    /** 이 스레드가 잡은 리스가 있으면 그 리스를 풀면서 새 마감시간을 기록한다. */
    private void schedule(String roomId, int seconds) {
        Lease held = HELD.get();
        String token = held != null && held.roomId().equals(roomId) ? held.token() : null;
        if (!repository.schedule(roomId, seconds * 1000L, token)) {
            // 처리하는 데 리스 시간보다 오래 걸려서 그 사이 다른 서버가 대신 진행을 시작했다. 그쪽이 다음 마감시간을 기록한다.
            log.warn("리스를 잃어 마감시간을 기록하지 못했습니다. roomId={}", roomId);
        }
        clearHeld(roomId);
    }

    // ===== 요청 처리 (GameService.validate가 부른다) =====

    /**
     * 요청을 처리해도 되는지 확인하고 리스를 잡는다.
     * 사람의 요청: 마감 전이고 처리 중인 다른 요청이 없을 때만 true. 마감시간을 리스 시간만큼 늘려 둔다.
     * 대신 진행: 이미 리스를 잡은 상태이므로 true. 사람이면 AFK로 표시한다. (봇은 AFK가 아니다)
     */
    public boolean tryAcquire(String roomId, long playerId, boolean bot) {
        Lease held = HELD.get();
        if (held != null && held.auto()) {
            if (!held.roomId().equals(roomId)) {
                return false;
            }
            if (!bot) {
                repository.addAfk(roomId, playerId);
            }
            return true;
        }
        if (held != null) {
            releaseHeld();   // 이 스레드가 이전 요청의 리스를 놓지 않았다. 먼저 되돌린다.
        }

        String token = UUID.randomUUID().toString();
        TimerRepository.Claim claim = repository.claimBeforeDeadline(roomId, token, leaseMillis);
        if (claim.status() != TimerRepository.ClaimStatus.OK) {
            return false;
        }
        HELD.set(new Lease(roomId, token, claim.previousDeadline(), false));
        return true;
    }

    /**
     * 요청 처리가 끝났다. 마감시간을 새로 기록하지 못한 채(예외) 끝났으면 리스를 풀고 마감시간을 원래대로 되돌린다.
     * 정상으로 끝났으면 saveAndSchedule이 이미 리스를 놓았으므로 아무것도 하지 않는다.
     * 대신 진행이 실패한 경우는 되돌리지 않는다. 리스 시간이 지나면 다시 대신 진행을 시도한다. (바로 되돌리면 실패를 계속 반복한다)
     */
    public void releaseHeld() {
        Lease held = HELD.get();
        if (held == null || held.auto()) {
            return;
        }
        HELD.remove();
        repository.restore(held.roomId(), held.token(), held.previousDeadline());
    }

    /** 사람이 요청을 보냈다. 요청이 거부되더라도 그 플레이어의 AFK를 푼다. (서버의 대신 진행이 부른 경우는 무시) */
    public void release(String roomId, long playerId) {
        if (isAutoPlaying()) {
            return;
        }
        repository.removeAfk(roomId, playerId);
    }

    public boolean isAfk(String roomId, long playerId) {
        return repository.isAfk(roomId, playerId);
    }

    // ===== 대신 진행 =====

    /** 마감이 지난 방을 찾아서, 리스를 잡은 방만 대신 진행한다. 다른 서버가 먼저 잡은 방은 건너뛴다. */
    private void poll() {
        if (handler == null) {
            return;
        }
        try {
            List<String> expired = repository.findExpired(POLL_BATCH);
            for (String roomId : expired) {
                String token = UUID.randomUUID().toString();
                if (repository.claimExpired(roomId, token, leaseMillis)) {
                    workers.execute(() -> autoPlay(roomId, token));
                }
            }
        } catch (Exception e) {
            log.error("마감시간 확인 실패", e);   // 예외가 나도 다음 확인은 계속된다
        }
    }

    private void autoPlay(String roomId, String token) {
        HELD.set(new Lease(roomId, token, 0, true));
        try {
            handler.onTimeout(roomId);
        } catch (Exception e) {
            log.warn("대신 진행 실패. {}ms 뒤에 다시 시도합니다. roomId={}", leaseMillis, roomId, e);
        } finally {
            HELD.remove();
        }
    }

    private static boolean isAutoPlaying() {
        Lease held = HELD.get();
        return held != null && held.auto();
    }

    private static void clearHeld(String roomId) {
        Lease held = HELD.get();
        if (held != null && held.roomId().equals(roomId)) {
            HELD.remove();
        }
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
