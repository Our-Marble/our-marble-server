package com.sparta.ourmarbleserver.global.websocket;

import com.sparta.ourmarbleserver.auth.dto.AuthPlayer;
import com.sparta.ourmarbleserver.config.JwtAuthenticationToken;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.Principal;
import java.util.Map;

/**
 * 웹소켓 연결(핸드셰이크) 직전에 "누가 접속하는지"를 확인해 세션에 playerId 이름표를 붙인다.
 * 이후 이 연결로 오는 모든 메시지는 이 playerId가 보낸 것으로 본다. (클라가 playerId를 직접 정하지 못하게)
 *
 * /ws 요청은 Spring Security(JwtAuthenticationFilter)가 Authorization: Bearer 토큰을 먼저 검증한다.
 * 토큰이 없거나 틀리면 여기까지 오지 않고 401로 끝나며, 통과하면 검증된 로그인 정보(AuthPlayer)에서 회원 번호만 꺼낸다.
 */
@Slf4j
@Component
public class PlayerHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        Long playerId = resolvePlayerId(request);
        if (playerId == null) {
            log.info("[WS] 연결 거부: playerId를 확인할 수 없음 ({})", request.getURI());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(MainWebSocketHandler.PLAYER_ID_ATTRIBUTE, playerId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               @Nullable Exception exception) {
    }

    /** Security가 검증해 둔 로그인 정보의 playerId. 없으면 null. */
    private Long resolvePlayerId(ServerHttpRequest request) {
        Principal principal = request.getPrincipal();
        if (principal instanceof JwtAuthenticationToken token
                && token.getPrincipal() instanceof AuthPlayer player) {
            return player.playerId();
        }
        return null;
    }
}
