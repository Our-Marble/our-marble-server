package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.websocket.SessionRegistry;
import com.sparta.ourmarbleserver.lobby.state.Room;
import com.sparta.ourmarbleserver.lobby.state.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRelay implements MessageListener
{
    // 발행과 구독 채널 이름을 이곳 한 곳에서 관리
    public static final String ROOM_CHANNEL_PREFIX = "room:";

    private final StringRedisTemplate redisTemplate;
    private final RoomBroadcaster roomBroadcaster;


    public void publish(
            String roomId,
            String jsonMessage
    ) {
        redisTemplate.convertAndSend(ROOM_CHANNEL_PREFIX + roomId, jsonMessage);
        log.info("발행: room={} {}", roomId, jsonMessage);
    }

    @Override
    public void onMessage(
            Message message,
            byte[] pattern
    ) {
        String channelName = new String(message.getChannel(), StandardCharsets.UTF_8);
        String jsonMessage = new String(message.getBody(), StandardCharsets.UTF_8);
        String roomId = channelName.substring(ROOM_CHANNEL_PREFIX.length());
        log.info("수신: room={} {}", roomId, jsonMessage);
        roomBroadcaster.broadcastJson(roomId, jsonMessage);
    }
}
