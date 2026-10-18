package com.sparta.ourmarbleserver.global.config;

import com.sparta.ourmarbleserver.global.transport.MessageRelay;
import com.sparta.ourmarbleserver.global.websocket.ServerInstance;
import com.sparta.ourmarbleserver.global.websocket.SessionKickListener;
import com.sparta.ourmarbleserver.global.websocket.SessionKickPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

// Redis Pub/Sub 구독 설정. 모든 방 채널(room:*)과 로비 채널, 이 서버 전용 채널(server:{서버id})을 구독한다. 방마다 따로 구독하지 않는다.
@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageRelay messageRelay,
            SessionKickListener sessionKickListener,
            ServerInstance serverInstance
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(messageRelay, new PatternTopic(MessageRelay.ROOM_CHANNEL_PREFIX + "*"));
        container.addMessageListener(messageRelay, new ChannelTopic(MessageRelay.LOBBY_CHANNEL));
        // 이 서버에게만 오는 요청 (다른 서버의 중복 접속으로 옛 연결 끊기)
        container.addMessageListener(sessionKickListener,
                new ChannelTopic(SessionKickPublisher.SERVER_CHANNEL_PREFIX + serverInstance.id()));
        return container;
    }
}
