package com.sparta.ourmarbleserver.global.transport;

import org.springframework.stereotype.Component;

import com.sparta.ourmarbleserver.global.protocol.MessageType;

import lombok.extern.slf4j.Slf4j;

/**
 * [임시] 네트워크 담당의 EventPublisher 구현이 들어오기 전까지 서버가 뜨도록 하는 구현체.
 * 실제로 보내지 않고 로그만 남긴다. 실제 구현이 합쳐지면 이 파일을 삭제한다. (빈이 둘이 되면 충돌)
 */
@Slf4j
@Component
public class LoggingEventPublisher implements EventPublisher {

    @Override
    public void publishToRoom(String roomId, MessageType type, Object payload) {
        log.info("[임시 EventPublisher] 방 {} 전원 <- {} {}", roomId, type, payload);
    }

    @Override
    public void sendToPlayer(String roomId, long playerId, MessageType type, Object payload) {
        log.info("[임시 EventPublisher] 방 {} 플레이어 {} <- {} {}", roomId, playerId, type, payload);
    }
}