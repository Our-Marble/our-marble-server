package com.sparta.ourmarbleserver.global.websocket;

import com.sparta.ourmarbleserver.game.dto.GameMessage;
import com.sparta.ourmarbleserver.global.transport.MessageRelay;
import com.sparta.ourmarbleserver.global.transport.OutboundMessage;
import com.sparta.ourmarbleserver.global.transport.PlayerUnicaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class WebSocketMessageSender {

    private final PlayerUnicaster playerUnicaster;
    private final MessageRelay messageRelay;
    private final ObjectMapper objectMapper;


    /*
    * GameMessage 클래스가 이름이 Game이 붙어있어서 Lobby쪽에선 못씀... Object 타입르로 받자니 내용물을 꺼내볼수가없음. 그래서 일단 게임용 메서드들과 로비용 메서드들 구분해둠.
    */

    // 게임용
    public void sendToPlayer(long playerId, GameMessage message)
    {
        OutboundMessage outboundMessage = new OutboundMessage(message.type(), message.payload());
        String jsonMessage = objectMapper.writeValueAsString(outboundMessage);

        playerUnicaster.sendToPlayer(playerId, jsonMessage);
    }

    public void publishToRoom(String roomId, GameMessage message)
    {
        OutboundMessage outboundMessage = new OutboundMessage(message.type(), message.payload());
        String jsonMessage = objectMapper.writeValueAsString(outboundMessage);

        messageRelay.publish(roomId, jsonMessage);
    }

    // 로비용
    public void sendToPlayer()
    {

    }

    public void publishToRoom()
    {

    }
}
