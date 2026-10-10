package com.sparta.ourmarbleserver.global.transport;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RedisPubSubManager {

    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final MessageListenerAdapter messageListenerAdapter;

    // 유저별로 등록된 Listener 맵핑 정보를 관리하기 위한 맵
    private final Map<Long, String> subscribedRooms = new ConcurrentHashMap<>();

    /**
     * 특정 방을 동적으로 구독
     */
    public void subscribeRoom(Long playerId, String roomId) {
        String channelName = "room:" + roomId;
        ChannelTopic topic = new ChannelTopic(channelName);

        // 이미 구독 중복 방지가 필요하다면 체크 로직 추가 가능

        // 동적으로 컨테이너에 리스너와 토픽 등록!
        redisMessageListenerContainer.addMessageListener(messageListenerAdapter, topic);

        // 나중에 구독을 해제(방 나가기)할 때 쓰기 위해 저장해둘 수 있음
        subscribedRooms.put(playerId, roomId);

        System.out.println("Player " + playerId + " subscribed to " + channelName);
    }

    /**
     * 특정 방 구독 해제 (방 나갈 때 등)
     */
    public void unsubscribeRoom(Long playerId, String roomId) {
        if (subscribedRooms.remove(playerId) != null) {
            String channelName = "room:" + roomId;
            ChannelTopic topic = new ChannelTopic(channelName);
            redisMessageListenerContainer.removeMessageListener(messageListenerAdapter, topic);
            System.out.println("Player " + playerId + " unsubscribed from " + topic.getTopic());
        }
    }
}