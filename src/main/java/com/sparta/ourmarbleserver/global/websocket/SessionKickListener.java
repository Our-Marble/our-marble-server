package com.sparta.ourmarbleserver.global.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 이 서버 채널(server:{서버id})로 온 "연결 끊기" 요청을 처리한다.
 * 같은 계정이 다른 서버에 새로 접속하면, 그 서버가 이 서버에 남은 옛 연결을 끊어 달라고 보낸다.
 * (보내는 쪽 SessionKickPublisher와 나눈 이유: 한 클래스면 SessionRegistry와 서로를 참조해 순환 의존이 생긴다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionKickListener implements MessageListener {

    private final SessionRegistry sessionRegistry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            int separator = body.indexOf(':');
            long playerId = Long.parseLong(body.substring(0, separator));
            String sessionId = body.substring(separator + 1);
            sessionRegistry.closeDuplicate(playerId, sessionId);
        } catch (RuntimeException e) {
            log.warn("[WS] 알 수 없는 서버 메시지: {}", body);
        }
    }
}
