package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.websocket.WebSocketMessageSender;
import com.sparta.ourmarbleserver.lobby.dto.EnterRoomRequestPayload;
import com.sparta.ourmarbleserver.lobby.dto.GameStartedPayload;
import com.sparta.ourmarbleserver.lobby.dto.SetReadyRequestPayload;
import com.sparta.ourmarbleserver.lobby.service.LobbyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class LobbyMessageHandler implements WsMessageHandler {

    private final LobbyService lobbyService;
    private final WebSocketMessageSender sender;
    private final ObjectMapper objectMapper;

    @Override
    public Set<MessageType> types() {
        return Set.of(
                MessageType.CREATE_ROOM,
                MessageType.ENTER_ROOM,
                MessageType.LEAVE_ROOM,
                MessageType.SET_READY,
                MessageType.START_GAME,
                MessageType.ROOM_LIST_REQUEST
        );
    }

    @Override
    public void handle(MessageType type, long playerId, JsonNode payload) {
        switch (type) {
            case CREATE_ROOM -> createRoom(playerId);
            case ENTER_ROOM -> enterRoom(playerId, objectMapper.treeToValue(payload, EnterRoomRequestPayload.class));
            case LEAVE_ROOM -> leaveRoom(playerId);
            case SET_READY -> setReady(playerId, objectMapper.treeToValue(payload, SetReadyRequestPayload.class));
            case START_GAME -> startGame(playerId);
            case ROOM_LIST_REQUEST -> sender.sendToPlayer(playerId, MessageType.ROOM_LIST, lobbyService.roomList()); // 방 목록은 자동으로 보내지 않는다. 로비 화면은 접속할 때와 새로고침(ROOM_LIST_REQUEST)할 때만 목록을 받는다.
            default -> throw new IllegalStateException("LobbyMessageHandler가 처리하지 않는 type: " + type);
        }
    }

    private void createRoom(long playerId) {
        String roomId = lobbyService.createRoom(playerId).roomId();
        notifyEntered(playerId, roomId);
    }

    private void enterRoom(long playerId, EnterRoomRequestPayload request) {
        if (request.roomId() == null || request.roomId().isBlank()) {
            throw new GameException(ErrorCode.INVALID_MESSAGE);
        }
        lobbyService.join(request.roomId(), playerId);
        notifyEntered(playerId, request.roomId());
    }

    // 방 생성/입장 성공: 본인 ROOM_ENTERED → 방 전원 ROOM_UPDATED
    private void notifyEntered(long playerId, String roomId) {
        sender.sendToPlayer(playerId, MessageType.ROOM_ENTERED, null);
        sender.sendToRoom(roomId, MessageType.ROOM_UPDATED, lobbyService.roomUpdated(roomId));
    }

    private void leaveRoom(long playerId) {
        String roomId = lobbyService.roomIdOf(playerId);
        boolean roomRemains = lobbyService.leave(roomId, playerId);

        sender.sendToPlayer(playerId, MessageType.ROOM_LEFT, null);
        sender.sendToPlayer(playerId, MessageType.ROOM_LIST, lobbyService.roomList());   // 로비로 돌아가니 최신 목록을 같이 준다
        if (roomRemains) {   // 마지막 사람이 나가 방이 지워졌으면 보낼 대상이 없다
            sender.sendToRoom(roomId, MessageType.ROOM_UPDATED, lobbyService.roomUpdated(roomId));
        }
    }

    private void setReady(long playerId, SetReadyRequestPayload request) {
        String roomId = lobbyService.roomIdOf(playerId);
        lobbyService.setReady(roomId, playerId, request.ready());
        sender.sendToRoom(roomId, MessageType.ROOM_UPDATED, lobbyService.roomUpdated(roomId));
    }

    private void startGame(long playerId) {
        String roomId = lobbyService.roomIdOf(playerId);
        lobbyService.start(roomId, playerId);
        sender.sendToRoom(roomId, MessageType.GAME_STARTED, new GameStartedPayload(roomId));
    }
}