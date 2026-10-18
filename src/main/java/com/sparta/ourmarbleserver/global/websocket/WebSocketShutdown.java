package com.sparta.ourmarbleserver.global.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 서버가 정상 종료될 때 이 서버의 WebSocket 연결을 가장 먼저 닫는다.
 * Spring은 종료 시 phase가 큰 Lifecycle부터 멈춘다. 가장 큰 값을 줘서 웹 서버(Tomcat)가 멈추기 전에 닫는다.
 * (늦으면 Tomcat이 먼저 소켓을 끊어 클라는 1006을 받고, 대기방 정리와 접속 기록 삭제도 못 한다)
 * 이 시점에는 Redis도 살아 있어서 afterConnectionClosed의 정리가 끝까지 돈다.
 */
@Component
@RequiredArgsConstructor
public class WebSocketShutdown implements SmartLifecycle {

    private final SessionRegistry sessionRegistry;
    private volatile boolean running;

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        sessionRegistry.closeAll();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;   // 가장 먼저 멈춘다
    }
}