package com.sparta.ourmarbleserver.global.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 서버 메모리에 접속 기록을 둔다. 테스트용 (실제 서버는 RedisPresenceRepository). TTL은 없다. */
public class InMemoryPresenceRepository implements PresenceRepository {

    private final Map<Long, String> tokens = new ConcurrentHashMap<>();

    @Override
    public void markOnline(long playerId, String token) {
        tokens.put(playerId, token);
    }

    @Override
    public void markOffline(long playerId, String token) {
        tokens.remove(playerId, token);   // 값이 token일 때만 지운다
    }

    @Override
    public void refresh(Map<Long, String> tokens) {
        // TTL이 없으므로 할 일 없음
    }

    @Override
    public boolean isOnline(long playerId) {
        return tokens.containsKey(playerId);
    }

    /** 테스트 확인용: 지금 기록된 token */
    public String tokenOf(long playerId) {
        return tokens.get(playerId);
    }
}