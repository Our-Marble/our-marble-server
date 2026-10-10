package com.sparta.ourmarbleserver.global.websocket;

import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ConnectedPayload;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.ErrorPayload;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.transport.InboundMessage;
import com.sparta.ourmarbleserver.global.transport.MessageCodec;
import com.sparta.ourmarbleserver.global.transport.MessageRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;

/**
 * 서버의 모든 웹소켓 통신(로비·대기방·게임)을 받는 단 하나의 창구.
 * 로그인 직후 한 번 연결해 끝까지 유지하며, 어떤 요청인지는 메시지의 type으로 구분한다.
 *
 * 연결/종료 시 SessionRegistry에 등록/제거
 * 받은 메시지를 MessageCodec으로 해석해 MessageRouter로 넘기고, 실패하면 요청자에게 ERROR 응답
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MainWebSocketHandler extends TextWebSocketHandler {

    /** 핸드셰이크 때 PlayerHandshakeInterceptor가 세션 속성에 playerId를 넣는 이름. 두 곳이 같아야 한다. */
    public static final String PLAYER_ID_ATTRIBUTE = "playerId";

    private final MessageCodec codec;
    private final SessionRegistry sessionRegistry;
    private final MessageRouter router;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        long playerId = playerIdOf(session);
        log.info("[WS] 연결: player={} session={}", playerId, session.getId());
        sessionRegistry.register(playerId, session);    // 등록 먼저
        send(playerId, MessageType.CONNECTED, new ConnectedPayload(playerId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        long playerId = playerIdOf(session);
        try {
            InboundMessage inbound = codec.decode(message.getPayload());
            log.info("[WS] 수신: player={} type={} body={}", playerId, inbound.type(), inbound.body());
            router.route(playerId, inbound);
        } catch (GameException e) {
            log.info("[WS] 요청 거부: player={} code={} message={}", playerId, e.code(), message.getPayload());
            sendError(playerId, e.code());
        } catch (JacksonException e) {
            // 핸들러가 body를 요청 DTO로 바꾸다 실패 (필드 타입이 틀림 등)
            log.info("[WS] 본문 해석 실패: player={} message={}", playerId, message.getPayload());
            sendError(playerId, ErrorCode.INVALID_MESSAGE);
        } catch (RuntimeException e) {
            // 예상 못 한 서버 오류. 연결은 유지하고 로그만 남긴다.
            log.error("[WS] 처리 중 오류: player={} message={}", playerId, message.getPayload(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("[WS] 종료: player={} session={} status={}", playerIdOf(session), session.getId(), status);
        sessionRegistry.remove(playerIdOf(session), session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[WS] 전송 오류: session={} {}", session.getId(), exception.getMessage());
    }

    /** 핸드셰이크를 통과한 세션에 붙어 있는 playerId. */
    private static long playerIdOf(WebSocketSession session) {
        return (Long) session.getAttributes().get(PLAYER_ID_ATTRIBUTE);
    }

    private void sendError(long playerId, ErrorCode code) {
        send(playerId, MessageType.ERROR, ErrorPayload.of(code));
    }

    // 한 명에게 보낸다. (2단계에서 WebSocketMessageSender로 옮길 예정)
    private void send(long playerId, MessageType type, Object payload) {
        sessionRegistry.send(playerId, codec.encode(type, payload));
    }
}