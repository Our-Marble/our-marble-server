package com.sparta.ourmarbleserver.global.websocket;

import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.transport.MessageCodec;
import com.sparta.ourmarbleserver.global.transport.MessageRelay;
import com.sparta.ourmarbleserver.global.transport.PlayerUnicaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 서버에서 클라이언트로 나가는 모든 메시지의 출구.
 * 무엇을(type, payload) 누구에게(한 명 / 방 전체) 보낼지만 받고, JSON 변환과 실제 전달은 아래에 맡긴다.
 *
 * 한 명   → PlayerUnicaster
 * 방 전체 → MessageRelay → RoomBroadcaster
 */
@Component
@RequiredArgsConstructor
public class WebSocketMessageSender {

    private final MessageCodec codec;
    private final PlayerUnicaster playerUnicaster;
    private final MessageRelay messageRelay;

    // 한 명에게 (응답, ERROR, CONNECTED 등)
    public void sendToPlayer(long playerId, MessageType type, Object payload) {
        playerUnicaster.sendToPlayer(playerId, codec.encode(type, payload));
    }

    // 방에 있는 모두에게
    public void sendToRoom(String roomId, MessageType type, Object payload) {
        messageRelay.publish(roomId, codec.encode(type, payload));
    }
}