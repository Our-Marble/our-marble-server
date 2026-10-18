package com.sparta.ourmarbleserver.global.transport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisInvalidSubscriptionException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/*
* 채널 종류:
* room:{roomId} -> 그 방 멤버에게
* lobby         -> 방에 들어가 있지 않은 (로비 화면) 사람에게
*/
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRelay implements MessageListener
{
    // 발행과 구독 채널 이름을 이곳 한 곳에서 관리
    public static final String ROOM_CHANNEL_PREFIX = "room:";
    public static final String LOBBY_CHANNEL = "lobby";

    private final StringRedisTemplate redisTemplate;
    private final RoomBroadcaster roomBroadcaster;


    public void publishToRoom(String roomId, String jsonMessage) {
        redisTemplate.convertAndSend(ROOM_CHANNEL_PREFIX + roomId, jsonMessage);
        log.info("발행: room={} {}", roomId, jsonMessage);
    }

    public void publishToLobby(String jsonMessage) {
        redisTemplate.convertAndSend(LOBBY_CHANNEL, jsonMessage);
        log.info("발행: lobby {}", jsonMessage);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String jsonMessage = new String(message.getBody(), StandardCharsets.UTF_8);

        if (LOBBY_CHANNEL.equals(channel)) {
            log.info("수신: lobby {}", jsonMessage);
            roomBroadcaster.broadcastLobbyJson(jsonMessage);
            return;
        }

        if (channel.startsWith(ROOM_CHANNEL_PREFIX)) {
            String roomId = channel.substring(ROOM_CHANNEL_PREFIX.length());
            log.info("수신: room={} {}", roomId, jsonMessage);
            roomBroadcaster.broadcastJson(roomId, jsonMessage);
            return;
        }

        log.warn("[MessageRelay] 알 수 없느 채널: {}", channel);
    }
}
