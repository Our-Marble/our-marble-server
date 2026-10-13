package com.sparta.ourmarbleserver.global.websocket;

import com.sparta.ourmarbleserver.auth.dto.AuthPlayer;
import com.sparta.ourmarbleserver.auth.entity.Role;
import com.sparta.ourmarbleserver.config.JwtAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerHandshakeInterceptorTest {

    private final PlayerHandshakeInterceptor interceptor = new PlayerHandshakeInterceptor();
    private final ServerHttpRequest request = mock(ServerHttpRequest.class);
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final Map<String, Object> attributes = new HashMap<>();

    @Test
    void 로그인_정보가_있으면_playerId를_세션에_넣고_통과한다() {
        when(request.getPrincipal())
                .thenReturn(new JwtAuthenticationToken(new AuthPlayer(7L, Role.USER), List.of()));

        boolean allowed = interceptor.beforeHandshake(request, response, null, attributes);

        assertThat(allowed).isTrue();
        assertThat(attributes).containsEntry(MainWebSocketHandler.PLAYER_ID_ATTRIBUTE, 7L);
    }

    @Test
    void 로그인_정보가_없으면_401로_거부한다() {
        when(request.getPrincipal()).thenReturn(null);
        when(request.getURI()).thenReturn(URI.create("ws://localhost/ws"));

        boolean allowed = interceptor.beforeHandshake(request, response, null, attributes);

        assertThat(allowed).isFalse();
        assertThat(attributes).isEmpty();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}