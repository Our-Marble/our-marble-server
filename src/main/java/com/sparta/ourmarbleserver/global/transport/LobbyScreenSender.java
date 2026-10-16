package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.auth.service.PlayerService;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.websocket.WebSocketMessageSender;
import com.sparta.ourmarbleserver.lobby.dto.PlayerRecordPayload;
import com.sparta.ourmarbleserver.lobby.service.LobbyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 로비 화면에 필요한 알림을 본인에게 보낸다. (ROOM_LIST → PLAYER_RECORD)
 * 접속, 새로고침, 방 나가기처럼 로비 화면을 보여 줄 때 이것만 부른다.
 * 전적 조회가 실패해도 방 목록은 이미 보냈으므로 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LobbyScreenSender {

    private final LobbyService lobbyService;
    private final PlayerService playerService;
    private final WebSocketMessageSender sender;

    public void send(long playerId) {
        sender.sendToPlayer(playerId, MessageType.ROOM_LIST, lobbyService.roomList());
        try {
            PlayerRecordPayload record = PlayerRecordPayload.from(playerService.findMyPlayRecord(playerId));
            sender.sendToPlayer(playerId, MessageType.PLAYER_RECORD, record);
        } catch (RuntimeException e) {
            log.warn("[WS] 전적 조회 실패: player={}", playerId, e);
        }
    }
}