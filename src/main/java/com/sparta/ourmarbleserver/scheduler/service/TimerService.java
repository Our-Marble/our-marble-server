package com.sparta.ourmarbleserver.scheduler.service;

import com.sparta.ourmarbleserver.game.state.TurnPhase;
import org.springframework.stereotype.Component;

import java.util.Map;
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

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Map<String, Long> tickets = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong();
    private Handler handler = (roomId, playerId, turnNumber, phase) -> { };   // 아직 아무것도 안 함

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    public void start(String roomId, long playerId, int turnNumber, TurnPhase phase, int limitSeconds) {
        long ticket = counter.incrementAndGet();
        tickets.put(roomId, ticket);
        executor.schedule(() -> {
            if (tickets.remove(roomId, ticket)) {
                handler.onTimeout(roomId, playerId, turnNumber, phase);
            }
        }, limitSeconds, TimeUnit.SECONDS);
    }

    public void cancel(String roomId) {
        tickets.remove(roomId);
    }
}