package com.sparta.ourmarbleserver.scheduler.repository;

import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;

import java.util.*;

/**
 * 서버 메모리에 마감시간을 보관하는 구현. (테스트용, 서버 한 대에서만 맞다)
 * RedisTimerRepository의 Lua 스크립트와 같은 규칙을 synchronized로 지킨다.
 * saveAndSchedule은 생성자로 받은 게임 상태 저장소에 상태를 저장한다. (받지 않았으면 타이머만 기록한다)
 */
public class InMemoryTimerRepository implements TimerRepository {

    private final GameStateRepository states;

    public InMemoryTimerRepository() {
        this(null);
    }

    public InMemoryTimerRepository(GameStateRepository states) {
        this.states = states;
    }

    private record Lease(String token, long expiresAt) {
    }

    private final Map<String, Long> deadlines = new HashMap<>();
    private final Map<String, Lease> leases = new HashMap<>();
    private final Map<String, Set<Long>> afkPlayers = new HashMap<>();

    @Override
    public synchronized Claim claimBeforeDeadline(String roomId, String token, long leaseMillis) {
        long now = now();
        Long deadline = deadlines.get(roomId);
        if (deadline != null && deadline <= now) {
            return new Claim(ClaimStatus.EXPIRED, deadline);
        }
        if (activeLease(roomId, now) != null) {
            return new Claim(ClaimStatus.BUSY, deadline == null ? 0 : deadline);
        }
        leases.put(roomId, new Lease(token, now + leaseMillis));
        if (deadline != null) {
            deadlines.put(roomId, now + leaseMillis);
        }
        return new Claim(ClaimStatus.OK, deadline == null ? 0 : deadline);
    }

    @Override
    public synchronized boolean claimExpired(String roomId, String token, long leaseMillis) {
        long now = now();
        Long deadline = deadlines.get(roomId);
        if (deadline == null || deadline > now) {
            return false;
        }
        leases.put(roomId, new Lease(token, now + leaseMillis));
        deadlines.put(roomId, now + leaseMillis);
        return true;
    }

    @Override
    public synchronized boolean schedule(String roomId, long delayMillis, String token) {
        long now = now();
        if (token != null) {
            Lease current = activeLease(roomId, now);
            if (current != null && !current.token().equals(token)) {
                return false;
            }
        }
        leases.remove(roomId);
        deadlines.put(roomId, now + delayMillis);
        return true;
    }

    @Override
    public synchronized boolean saveAndSchedule(GameState state, long delayMillis, String token) {
        String roomId = state.getRoomId();
        long now = now();
        if (token != null) {
            Lease current = activeLease(roomId, now);
            if (current != null && !current.token().equals(token)) {
                return false;
            }
        }
        if (states != null) {
            states.save(state);
        }
        leases.remove(roomId);
        if (delayMillis < 0) {
            deadlines.remove(roomId);
            afkPlayers.remove(roomId);
        } else {
            deadlines.put(roomId, now + delayMillis);
        }
        return true;
    }

    @Override
    public synchronized void restore(String roomId, String token, long previousDeadline) {
        Lease current = activeLease(roomId, now());
        if (current == null || !current.token().equals(token)) {
            return;
        }
        leases.remove(roomId);
        if (previousDeadline > 0) {
            deadlines.put(roomId, previousDeadline);
        }
    }

    @Override
    public synchronized List<String> findExpired(int limit) {
        long now = now();
        return deadlines.entrySet().stream()
                .filter(entry -> entry.getValue() <= now)
                .sorted(Map.Entry.comparingByValue())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public synchronized void remove(String roomId) {
        deadlines.remove(roomId);
        leases.remove(roomId);
        afkPlayers.remove(roomId);
    }

    @Override
    public synchronized void addAfk(String roomId, long playerId) {
        afkPlayers.computeIfAbsent(roomId, key -> new HashSet<>()).add(playerId);
    }

    @Override
    public synchronized void removeAfk(String roomId, long playerId) {
        Set<Long> afk = afkPlayers.get(roomId);
        if (afk != null) {
            afk.remove(playerId);
        }
    }

    @Override
    public synchronized boolean isAfk(String roomId, long playerId) {
        return afkPlayers.getOrDefault(roomId, Set.of()).contains(playerId);
    }

    /** 리스 시간이 지난 리스는 없는 것으로 본다. (Redis 구현의 PX 만료와 같다) */
    private Lease activeLease(String roomId, long now) {
        Lease lease = leases.get(roomId);
        if (lease != null && lease.expiresAt() <= now) {
            leases.remove(roomId);
            return null;
        }
        return lease;
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
