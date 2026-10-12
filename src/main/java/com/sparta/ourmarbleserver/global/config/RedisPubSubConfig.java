package com.sparta.ourmarbleserver.global.config;

import com.sparta.ourmarbleserver.global.transport.MessageRelay;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

// Redis Pub/Sub 구독 설정. 모든 방 채널(room:*)을 한 번에 구독한다. 방마다 따로 구독하지 않는다.
@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageRelay messageRelay
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(messageRelay, new PatternTopic(MessageRelay.ROOM_CHANNEL_PREFIX + "*"));
        return container;
    }
}
