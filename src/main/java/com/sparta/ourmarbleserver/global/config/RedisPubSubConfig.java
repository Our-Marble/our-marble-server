package com.sparta.ourmarbleserver.global.config;

import com.sparta.ourmarbleserver.global.transport.MessageRelay;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {
    private final RedisConnectionFactory connectionFactory;
    private final MessageRelay messageRelay;

    @Bean
    public MessageListenerAdapter listenerAdapter() {
        // MessageRelay 클래스의 "onMessage"라는 이름의 메서드로 메시지를 전달하겠다는 의미
        return new MessageListenerAdapter(messageRelay, "onMessage");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(messageRelay, new PatternTopic("room:*"));
        return container;
    }
}
