package com.sparta.ourmarbleserver.global.transport;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.sparta.ourmarbleserver.global.protocol.MessageType;

public record OutboundMessage(
        MessageType type,
        @JsonUnwrapped
        Object payload
) {
}
