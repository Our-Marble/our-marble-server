package com.sparta.ourmarbleserver.scheduler.service;

import com.sparta.ourmarbleserver.game.state.TurnPhase;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TimerService {
    public interface Handler {
        void onTimeout(String roomId, long playerId, int turnNumber, TurnPhase phase);
    }

    /** 시간 초과가 난(AFK) 사람은 제한 시간 대신 이 시간만 기다린 뒤 바로 대신 진행한다. */
    private static final int AFK_DELAY_SECONDS = 3;

    /** 지금 스레드가 대신 진행 중인지. 대신 진행이 부른 start와 사람의 요청이 부른 start를 구분한다. */
    private static final ThreadLocal<Boolean> AUTO_PLAYING = ThreadLocal.withInitial(() -> false);

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Map<String, Long> tickets = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong();
    private final Map<String, Long> waiting = new ConcurrentHashMap<>();         // 방별로 지금 입력을 기다리는 플레이어
    private final Map<String, Set<Long>> afkPlayers = new ConcurrentHashMap<>(); // 방별 AFK 플레이어

    @Setter
    private Handler handler = (roomId, playerId, turnNumber, phase) -> { };   // 아직 아무것도 안 함

    /** 사람 차례의 타이머를 처음부터 새로 시작한다. AFK인 사람은 제한 시간 대신 짧게 기다린다. */
    public synchronized void start(String roomId, long playerId, int turnNumber, TurnPhase phase, int limitSeconds) {
        releasePrevious(roomId, playerId);
        int seconds = afk(roomId).contains(playerId) ? AFK_DELAY_SECONDS : limitSeconds;
        schedule(roomId, playerId, turnNumber, phase, seconds, true);
    }

    /** 봇 차례의 타이머를 시작한다. 봇은 AFK가 아니므로 AFK로 표시하지 않고, 항상 delaySeconds 뒤에 진행한다. */
    public synchronized void startBot(String roomId, long playerId, int turnNumber, TurnPhase phase, int delaySeconds) {
        releasePrevious(roomId, playerId);
        schedule(roomId, playerId, turnNumber, phase, delaySeconds, false);
    }

    /** 지금까지 입력을 기다리던 플레이어를 바꾼다. 사람이 직접 입력해서 불린 것이면 그 플레이어의 AFK를 푼다. */
    private void releasePrevious(String roomId, long playerId) {
        Long previous = waiting.put(roomId, playerId);
        if (previous != null && !AUTO_PLAYING.get()) {
            afk(roomId).remove(previous);
        }
    }

    private void schedule(String roomId, long playerId, int turnNumber, TurnPhase phase, int seconds, boolean humanTurn) {
        long ticket = counter.incrementAndGet();
        tickets.put(roomId, ticket);
        executor.schedule(() -> {
            if (markTimedOut(roomId, ticket, playerId, humanTurn)) {
                AUTO_PLAYING.set(true);
                try {
                    handler.onTimeout(roomId, playerId, turnNumber, phase);
                } finally {
                    AUTO_PLAYING.set(false);
                }
            }
        }, seconds, TimeUnit.SECONDS);
    }

    /**
     * 번호표가 아직 유효하면 시간이 지난 것으로 본다. 사람이면 AFK로 표시한다. (봇은 AFK가 아니라서 표시하지 않는다)
     * 사람의 AFK 해제와 같은 락을 쓰기 때문에, 타이머가 울린 직후에 사람이 입력해도 그 사람이 AFK로 되돌려지지 않는다.
     */
    private synchronized boolean markTimedOut(String roomId, long ticket, long playerId, boolean humanTurn) {
        if (!tickets.remove(roomId, ticket)) {
            return false;
        }
        if (humanTurn) {
            afk(roomId).add(playerId);
        }
        return true;
    }

    /** 사람이 요청을 보냈다. 요청이 거부되더라도 그 플레이어의 AFK를 푼다. (서버의 대신 진행이 부른 경우는 무시) */
    public synchronized void release(String roomId, long playerId) {
        if (!AUTO_PLAYING.get()) {
            afk(roomId).remove(playerId);
        }
    }

    /**
     * 서버가 이 플레이어를 대신 진행해도 되는지. 사람이 보낸 요청(타이머 스레드가 아닌 경우)은 항상 true.
     * 대신 진행은 봇이거나 AFK인 사람에게만 허용한다. 타이머가 울린 뒤 사람이 먼저 움직였다면 AFK가 풀려 있어서 거부된다.
     */
    public synchronized boolean canAutoPlay(String roomId, long playerId, boolean bot) {
        return !AUTO_PLAYING.get() || bot || afk(roomId).contains(playerId);
    }

    public synchronized void cancel(String roomId) {
        tickets.remove(roomId);
        waiting.remove(roomId);
        afkPlayers.remove(roomId);
    }

    private Set<Long> afk(String roomId) {
        return afkPlayers.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet());
    }
}