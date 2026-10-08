package com.sparta.ourmarbleserver.global.config;

import com.sparta.ourmarbleserver.global.websocket.GameWebSocketHandler;
import com.sparta.ourmarbleserver.global.websocket.PlayerHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final GameWebSocketHandler gameHandler;
    private final PlayerHandshakeInterceptor interceptor;

    @Override
    public void registerWebSocketHandlers(
            WebSocketHandlerRegistry registry
    ) {
        registry.addHandler(gameHandler, "/ws")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns("*");
    }
}
