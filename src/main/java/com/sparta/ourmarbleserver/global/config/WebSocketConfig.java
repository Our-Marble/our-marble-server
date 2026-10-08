package com.sparta.ourmarbleserver.global.config;

import com.sparta.ourmarbleserver.global.websocket.MainWebSocketHandler;
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
    private final MainWebSocketHandler mainHandler;
    private final PlayerHandshakeInterceptor interceptor;

    @Override
    public void registerWebSocketHandlers(
            WebSocketHandlerRegistry registry
    ) {
        registry.addHandler(mainHandler, "/ws")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns("*");
    }
}
