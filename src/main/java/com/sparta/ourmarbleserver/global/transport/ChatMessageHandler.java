package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.CommonErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.websocket.WebSocketMessageSender;
import com.sparta.ourmarbleserver.lobby.dto.ChatBroadcastPayload;
import com.sparta.ourmarbleserver.lobby.dto.ChatSendRequestPayload;
import com.sparta.ourmarbleserver.lobby.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class ChatMessageHandler implements WsMessageHandler {

    private final ChatService chatService;
    private final WebSocketMessageSender sender;
    private final ObjectMapper objectMapper;

    @Override
    public Set<MessageType> types() {
        return Set.of(MessageType.CHAT_SEND);
    }

    @Override
    public void handle(MessageType type, long playerId, JsonNode payload) {
        sendChat(playerId, objectMapper.treeToValue(payload, ChatSendRequestPayload.class));
    }

    // content가 없으면(null) INVALID_MESSAGE. 빈 문자열은 ChatService가 보낼 방이 없다고 알려 줘서 그냥 넘어간다.
    // 성공하면 보낸 본인을 포함한 방 전원에게 CHAT_BROADCAST
    private void sendChat(long playerId, ChatSendRequestPayload request) {
        if (request.content() == null) {
            throw new GameException(CommonErrorCode.INVALID_MESSAGE);
        }
        chatService.roomIdToSend(playerId, request.content())
                .ifPresent(roomId -> sender.sendToRoom(
                        roomId, MessageType.CHAT_BROADCAST, new ChatBroadcastPayload(playerId, request.content())));
    }
}
