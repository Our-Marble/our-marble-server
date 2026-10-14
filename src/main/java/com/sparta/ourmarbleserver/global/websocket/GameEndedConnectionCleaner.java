package com.sparta.ourmarbleserver.global.websocket;

import com.sparta.ourmarbleserver.game.event.GameEndedEvent;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.lobby.service.LobbyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 게임이 끝나 방이 대기 상태로 돌아갈 때, 게임 중에 연결이 끊긴 멤버를 방에서 내보낸다.
 * (끊긴 사람이 남은 사람들의 대기방 화면에 계속 보이지 않게 한다)
 * 접속 여부는 SessionRegistry가 알고, 방에서 빼는 일은 LobbyService가 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameEndedConnectionCleaner {

    private final SessionRegistry sessionRegistry;
    private final LobbyService lobbyService;
    private final WebSocketMessageSender sender;

    @EventListener
    @Order(2)   // LobbyService.onGameEnded(@Order(1))가 방을 WAITING으로 되돌린 뒤에 실행
    public void onGameEnded(GameEndedEvent event) {
        String roomId = event.roomId();
        try {
            List<Long> disconnected = lobbyService.memberIdsOf(roomId).stream()
                    .filter(id -> !sessionRegistry.isConnected(id))
                    .toList();
            if (disconnected.isEmpty()) {
                return;
            }
            log.info("[WS] 게임 종료 후 끊긴 멤버 정리: room={} players={}", roomId, disconnected);
            if (lobbyService.removeMembers(roomId, disconnected)) {
                sender.sendToRoom(roomId, MessageType.ROOM_UPDATED, lobbyService.roomUpdated(roomId));
            }
        } catch (RuntimeException e) {
            // 게임 요청을 처리하는 스레드에서 불리므로, 여기서 예외가 나도 게임 쪽으로 번지지 않게 막는다
            log.error("[WS] 게임 종료 후 정리 실패: room={}", roomId, e);
        }
    }
}