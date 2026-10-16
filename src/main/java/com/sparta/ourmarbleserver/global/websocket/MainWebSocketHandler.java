package com.sparta.ourmarbleserver.global.websocket;

import com.sparta.ourmarbleserver.auth.service.PlayerService;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.*;
import com.sparta.ourmarbleserver.global.transport.InboundMessage;
import com.sparta.ourmarbleserver.global.transport.LobbyScreenSender;
import com.sparta.ourmarbleserver.global.transport.MessageCodec;
import com.sparta.ourmarbleserver.global.transport.MessageRouter;
import com.sparta.ourmarbleserver.lobby.dto.GameStartedPayload;
import com.sparta.ourmarbleserver.lobby.service.LobbyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;

import java.io.IOException;

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
    private final WebSocketMessageSender sender;
    private final LobbyScreenSender lobbyScreenSender;
    private final PlayerService playerService;
    private final LobbyService lobbyService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        long playerId = playerIdOf(session);
        log.info("[WS] 연결: player={} session={}", playerId, session.getId());

        // nickname 찾기
        String nickname;
        try {
            nickname = playerService.findMe(playerId).getNickname();
        } catch (ResponseStatusException e) {
            // 토큰은 유효하지만 회원이 없음 (탈퇴 등) > 연결 거부
            log.info("[WS] 연결 거부: 회원 없음 player={}", playerId);
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        sessionRegistry.register(playerId, session);    // 등록 먼저

        sender.sendToPlayer(playerId, MessageType.CONNECTED, new ConnectedPayload(playerId, nickname)); // 연결 완료 알림

        // 게임이 끝나 대기방으로 돌아간 방에 남아 있었다면 정리하고 로비로 보낸다
        lobbyService.leaveWaitingRoom(playerId)
                .ifPresent(roomId -> sender.sendToRoom(roomId, MessageType.ROOM_UPDATED, lobbyService.roomUpdated(roomId)));

        // 게임 중인 방에 있으면 게임 화면으로, 아니면 로비 목록(방 목록 + 전적)
        lobbyService.playingRoomIdOf(playerId).ifPresentOrElse(
                roomId -> sender.sendToPlayer(playerId, MessageType.GAME_STARTED, new GameStartedPayload(roomId)),
                () -> lobbyScreenSender.send(playerId));
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
            sendError(playerId, CommonErrorCode.INVALID_MESSAGE);
        } catch (RuntimeException e) {
            // 예상 못 한 서버 오류. 연결은 유지하고 로그만 남긴다.
            log.error("[WS] 처리 중 오류: player={} message={}", playerId, message.getPayload(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        long playerId = playerIdOf(session);
        log.info("[WS] 종료: player={} session={} status={}", playerId, session.getId(), status);
        sessionRegistry.remove(playerId, session);

        if (sessionRegistry.isConnected(playerId)) {
            return;   // 중복 로그인으로 옛 연결만 끊긴 것: 새 연결이 살아 있으니 방은 그대로
        }
        try {
            lobbyService.leaveWaitingRoom(playerId)   // 대기방이면 퇴장, 게임 중이면 그대로
                    .ifPresent(roomId -> sender.sendToRoom(roomId, MessageType.ROOM_UPDATED, lobbyService.roomUpdated(roomId)));
                    // 해당 플레이어가 나가도 대기방이 남아있으면 방에 ROOM_UPDATED 브로드캐스트
        } catch (RuntimeException e) {
            log.error("[WS] 종료 정리 실패: player={}", playerId, e);
        }
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
        sender.sendToPlayer(playerId, code.type(), ErrorPayload.of(code));   // 오류 종류에 따라 COMMON_ERROR / LOBBY_ERROR / GAME_ERROR
    }
}