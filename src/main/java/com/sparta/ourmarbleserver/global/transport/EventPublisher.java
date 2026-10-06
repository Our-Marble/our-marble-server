package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.protocol.MessageType;

/**
 * 서버가 클라이언트에게 결과를 내보내는 출구.
 * 봉투 만들기, seq 부여, JSON 변환, 실제 전송은 구현체가 맡고,
 * 도메인 서비스는 payload dto만 만들어 넘긴다.
 */
public interface EventPublisher {

    /** 방의 모든 플레이어에게 보낸다. (결과 알림) */
    void publishToRoom(String gameId, MessageType type, Object payload);

    /** 한 명에게만 보낸다. (ERROR 등) */
    void sendToPlayer(String gameId, long playerId, MessageType type, Object payload);
}