package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.protocol.MessageType;
import tools.jackson.databind.JsonNode;

import java.util.Set;

public interface WsMessageHandler {
    Set<MessageType> types();
    void handle(MessageType type, long playerId, JsonNode payload);
}
