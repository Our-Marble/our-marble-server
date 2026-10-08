package com.sparta.ourmarbleserver.global.websocket;

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

import java.util.Map;

/**
 * 웹소켓 연결(핸드셰이크) 직전에 "누가 접속하는지"를 확인해 세션에 playerId 이름표를 붙인다.
 * 이후 이 연결로 오는 모든 메시지는 이 playerId가 보낸 것으로 본다. (클라가 playerId를 직접 정하지 못하게)
 *
 * 지금은 로그인(JWT)이 합쳐지기 전이라 테스트용으로 주소의 ?playerId= 값을 받는다.
 * 누구나 다른 번호를 적을 수 있으므로 marble.ws.allow-query-player-id=true 일 때만 허용한다.
 * (내 PC 전용 application-local.properties에서만 켜고, 실서버에서는 꺼져 있다)
 * JWT가 들어오면 토큰을 검증하고 그 안의 회원 번호를 playerId로 쓰도록 resolvePlayerId 바꿀 예정
 */
@Slf4j
@Component
public class PlayerHandshakeInterceptor implements HandshakeInterceptor {

    private static final String QUERY_PLAYER_ID = "playerId";

    private final boolean allowQueryPlayerId;

    public PlayerHandshakeInterceptor(
            @Value("${marble.ws.allow-query-player-id:false}") boolean allowQueryPlayerId
    ) {
        this.allowQueryPlayerId = allowQueryPlayerId;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
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
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, @Nullable Exception exception) {

    }

    /** 확인된 playerId. 확인할 수 없으면 null. */
    private Long resolvePlayerId(ServerHttpRequest request) {
        if (!allowQueryPlayerId) {
            return null;
        }
        String value = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst(QUERY_PLAYER_ID);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
