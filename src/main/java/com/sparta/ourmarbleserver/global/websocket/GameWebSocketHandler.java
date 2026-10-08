package com.sparta.ourmarbleserver.global.websocket;

import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ConnectedPayload;
import com.sparta.ourmarbleserver.global.protocol.ErrorPayload;
import com.sparta.ourmarbleserver.global.protocol.MessageTopic;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.transport.InboundMessage;
import com.sparta.ourmarbleserver.global.transport.MessageCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

/**
 * 웹소켓 연결 하나하나를 받는 곳
 * 연결되면 CONNECTED로 playerId를 알려 주고, 메시지가 오면 번역기(MessageCodec)로 해석한다.
 *
 * 연결/종료 시 SessionRegistry에 등록/제거
 * 연결 시 로비 구독 + ROOM_LIST 전송
 * 해석한 요청을 MessageRouter로 넘기고, GameException은 요청 topic으로 ERROR 응답
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler extends TextWebSocketHandler {

    /** 핸드셰이크 때 PlayerHandshakeInterceptor가 세션 속성에 playerId를 넣는 이름. 두 곳이 같아야 한다. */
    public static final String PLAYER_ID_ATTRIBUTE = "playerId";

    private final MessageCodec codec;

    @Override
    public void afterConnectionEstablished(
            WebSocketSession session
    ) throws Exception {
        long playerId = playerIdOf(session);
        log.info("[WS] 연결: player={} session={}", playerId, session.getId());
        send(session, MessageTopic.LOBBY, MessageType.CONNECTED, new ConnectedPayload(playerId));
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) {
        long playerId = playerIdOf(session);
        InboundMessage inbound;
        try {
            inbound = codec.decode(message.getPayload());
        } catch (GameException e) {
            // 해석할 수 없는 메시지는 topic을 알 수 없으므로 LOBBY로 고정해 돌려준다.
            log.info("[WS] 해석 실패: player={} message={}", playerId, message.getPayload());
            send(session, MessageTopic.LOBBY, MessageType.ERROR, ErrorPayload.of(e.code()));
            return;
        }
        log.info("[WS] 수신: player={} topic={} type={} body={}",
                playerId, inbound.topic(), inbound.type(), inbound.body());
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) {
        log.info("[WS] 종료: player={} session={} status={}", playerIdOf(session), session.getId(), status);
    }

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception
    ) {
        log.warn("[WS] 전송 오류: session={} {}", session.getId(), exception.getMessage());
    }

    /** 핸드셰이크를 통과한 세션에 붙어 있는 playerId. */
    private static long playerIdOf(WebSocketSession session) {
        return (Long) session.getAttributes().get(PLAYER_ID_ATTRIBUTE);
    }

    /**
     * 한 명에게 보낸다.
     * 같은 세션에 여러 스레드가 동시에 보내면 예외가 나므로 3단계에서 세션을 감싸(Decorator) 보호한다.
     */
    private void send(WebSocketSession session, MessageTopic topic, MessageType type, Object payload) {
        try {
            session.sendMessage(new TextMessage(codec.encode(topic, type, payload)));
        } catch (IOException e) {
            log.warn("[WS] 전송 실패: session={} type={} {}", session.getId(), type, e.getMessage());
        }
    }
}
