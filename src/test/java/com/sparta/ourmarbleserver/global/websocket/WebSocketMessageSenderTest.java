package com.sparta.ourmarbleserver.global.websocket;

import com.sparta.ourmarbleserver.global.protocol.ConnectedPayload;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.transport.MessageCodec;
import com.sparta.ourmarbleserver.global.transport.MessageRelay;
import com.sparta.ourmarbleserver.global.transport.PlayerUnicaster;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WebSocketMessageSenderTest {

    private final PlayerUnicaster unicaster = mock(PlayerUnicaster.class);
    private final MessageRelay relay = mock(MessageRelay.class);
    private final WebSocketMessageSender sender =
            new WebSocketMessageSender(new MessageCodec(new ObjectMapper()), unicaster, relay);

    @Test
    void 한_명에게_보내면_평평한_JSON으로_유니캐스터에_넘긴다() {
        sender.sendToPlayer(7L, MessageType.CONNECTED, new ConnectedPayload(7L));

        verify(unicaster).sendToPlayer(7L, "{\"type\":\"CONNECTED\",\"playerId\":7}");
        verifyNoInteractions(relay);
    }

    @Test
    void 방_전체에_보내면_릴레이에_방_id와_JSON을_넘긴다() {
        sender.sendToRoom("r_1", MessageType.CONNECTED, new ConnectedPayload(7L));

        verify(relay).publish("r_1", "{\"type\":\"CONNECTED\",\"playerId\":7}");
        verifyNoInteractions(unicaster);
    }
}