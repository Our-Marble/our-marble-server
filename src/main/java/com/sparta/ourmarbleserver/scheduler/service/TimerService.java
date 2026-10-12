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

    /** 시간 초과가 난(AFK) 플레이어는 제한 시간 대신 이 시간만 기다린 뒤 바로 대신 진행한다. */
    private static final int AFK_DELAY_SECONDS = 1;

    /** 지금 스레드가 대신 진행 중인지. 대신 진행이 부른 start와 사람의 요청이 부른 start를 구분한다. */
    private static final ThreadLocal<Boolean> AUTO_PLAYING = ThreadLocal.withInitial(() -> false);

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Map<String, Long> tickets = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong();
    private final Map<String, Long> waiting = new ConcurrentHashMap<>();         // 방별로 지금 입력을 기다리는 플레이어
    private final Map<String, Set<Long>> afkPlayers = new ConcurrentHashMap<>(); // 방별 AFK 플레이어

    @Setter
    private Handler handler = (roomId, playerId, turnNumber, phase) -> { };   // 아직 아무것도 안 함

    public void start(String roomId, long playerId, int turnNumber, TurnPhase phase, int limitSeconds) {
        Long previous = waiting.put(roomId, playerId);
        if (previous != null && !AUTO_PLAYING.get()) {
            afk(roomId).remove(previous);   // 사람이 직접 입력했다: 입력한 플레이어의 AFK를 푼다
        }
        int seconds = afk(roomId).contains(playerId) ? AFK_DELAY_SECONDS : limitSeconds;

        long ticket = counter.incrementAndGet();
        tickets.put(roomId, ticket);
        executor.schedule(() -> {
            if (tickets.remove(roomId, ticket)) {
                afk(roomId).add(playerId);   // 시간이 지났다: AFK로 판정한다
                AUTO_PLAYING.set(true);
                try {
                    handler.onTimeout(roomId, playerId, turnNumber, phase);
                } finally {
                    AUTO_PLAYING.set(false);
                }
            }
        }, seconds, TimeUnit.SECONDS);
    }

    public void cancel(String roomId) {
        tickets.remove(roomId);
        waiting.remove(roomId);
        afkPlayers.remove(roomId);
    }

    private Set<Long> afk(String roomId) {
        return afkPlayers.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet());
    }
}