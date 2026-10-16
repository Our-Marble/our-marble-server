package com.sparta.ourmarbleserver.lobby.dto;

/**
 * CHAT_BROADCAST 알림: 방에서 채팅이 왔다. 보낸 본인을 포함해 방 전원에게 보낸다.
 * 시각 필드는 없다. 클라이언트가 받은 시각을 쓴다.
 * <pre>
 * { "type": "CHAT_BROADCAST", "playerId": 1, "content": "하이" }
 * </pre>
 */
public record ChatBroadcastPayload(long playerId, String content) {
}
