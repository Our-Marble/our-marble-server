package com.sparta.ourmarbleserver.global.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
* 한 플레이어는 하나의 연결만 가짐
* 같은 playerId로 재접속 시 이전 연결 끊고 새 연결로 교체
*/
@Slf4j
@Component
public class SessionRegistry {
    // 전송 제한 시간(ms). 상대가 이 시간 안에 못 받으면 연결을 끊는다.
    private static final int SEND_TIME_LIMIT = 10_000;

    // 아직 못 보낸 메시지를 쌓아둘 수 있는 최대 크기(byte). 넘으면 연결을 끊는다. (서버 메모리 보호)
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 연결 등록
    public WebSocketSession register(long playerId, WebSocketSession session) {
        // 1. Decorator로 감싸기
        // 여러 스레드가 동시에 같은 세션으로 보내면 예외가 나므로, 보호막(Decorator)으로 감싸 한 번에 하나씩 보내게 한다.
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT, BUFFER_SIZE_LIMIT);

        // 2. put → previous 받기
        WebSocketSession previous = sessions.put(playerId, decorated);
        log.info("[WS] 등록: player={} session={}", playerId, session.getId());

        // 3. previous 있으면 DUPLICATE_LOGIN으로 close (try-catch)
        if (previous != null && previous.isOpen()) {
            log.info("[WS] 중복 접속: player={} 옛 session={} 종료", playerId, previous.getId());
            try {
                previous.close(SessionCloseCodes.DUPLICATE_LOGIN);
            } catch (IOException e) {
                log.warn("[WS] 옛 연결 종료 실패: player={} {}", playerId, e.getMessage());
            }
        }

        // 4. 감싼 세션 반환
        return decorated;
    }

    // 연결 삭제
    public void remove(long playerId, WebSocketSession session) {
        sessions.computeIfPresent(playerId, (id, current) -> {
            // 현재 연결과 세션 연결이 일치할 때만 삭제 (재접속 후 옛 연결의 종료 알림이 늦게 와도 새 연결이 지워지지 않게)
            // Decorator와 그냥 session 비교 불가능으로 Id를 통해 비교
            if (current.getId().equals(session.getId())) {
                log.info("[WS] 제거: player={} session={}", playerId, session.getId());
                return null;   // null을 돌려주면 Map에서 지워진다
            }
            return current;    // 다른 연결(새 연결)이면 그대로 둔다
        });
    }

    // 한 명에게 보내기
    public void send(long playerId, String json) {
        WebSocketSession session = sessions.get(playerId);
        if (session == null || !session.isOpen()) {
            log.info("[WS] 전송 생략(접속 안 함): player={}", playerId);
            return;
        }
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException | IllegalStateException e) {
            // IllegalStateException: 보호막의 시간/크기 제한을 넘겨 연결이 정리되는 중일 때
            log.warn("[WS] 전송 실패: player={} {}", playerId, e.getMessage());
        }
    }

    public boolean isConnected(long playerId) {
        WebSocketSession session = sessions.get(playerId);
        return session != null && session.isOpen();
    }
}
