package com.sparta.ourmarbleserver.global.transport;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MessageRelayTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RoomBroadcaster roomBroadcaster = mock(RoomBroadcaster.class);
    private final MessageRelay relay = new MessageRelay(redisTemplate, roomBroadcaster);

    @Test
    void 발행하면_room_채널로_보낸다() {
        relay.publish("r_1", "{\"type\":\"TEST\"}");

        verify(redisTemplate).convertAndSend("room:r_1", "{\"type\":\"TEST\"}");
    }

    @Test
    void 수신하면_채널에서_방_id를_꺼내_브로드캐스터로_넘긴다() {
        DefaultMessage message = new DefaultMessage(
                "room:r_1".getBytes(StandardCharsets.UTF_8),
                "{\"type\":\"TEST\"}".getBytes(StandardCharsets.UTF_8));

        relay.onMessage(message, null);

        verify(roomBroadcaster).broadcastJson("r_1", "{\"type\":\"TEST\"}");
    }
}