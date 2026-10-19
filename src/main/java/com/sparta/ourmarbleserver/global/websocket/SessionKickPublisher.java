package com.sparta.ourmarbleserver.global.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 다른 서버에게 "그 서버에 있는 이 연결을 끊어 달라"고 요청한다.
 * 서버마다 자기 채널(server:{서버id})을 구독하고, 받으면 SessionKickListener가 처리한다.
 * 메시지 본문: "{playerId}:{sessionId}"
 */
@Component
@RequiredArgsConstructor
public class SessionKickPublisher {

    public static final String SERVER_CHANNEL_PREFIX = "server:";

    private final StringRedisTemplate redisTemplate;

    public void kick(String serverId, long playerId, String sessionId) {
        redisTemplate.convertAndSend(SERVER_CHANNEL_PREFIX + serverId, playerId + ":" + sessionId);
    }
}
