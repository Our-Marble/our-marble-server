package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.websocket.SessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PlayerUnicaster {

    private final SessionRegistry sessionRegistry;

    public void sendToPlayer(long playerId, String jsonMessage){
        sessionRegistry.send(playerId, jsonMessage);
    }
}
