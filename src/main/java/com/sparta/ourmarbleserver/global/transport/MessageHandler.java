package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.protocol.MessageType;
import tools.jackson.databind.JsonNode;

/**
 * 클라이언트 요청을 받아 처리하는 쪽의 규격.
 * 도메인 서비스가 구현하고, 전송 계층(MessageRouter)이 type()으로 찾아 handle()을 호출한다.
 */
public interface MessageHandler {

    /** 이 핸들러가 담당하는 요청 종류. */
    MessageType type();

    /**
     * 요청이 오면 호출된다.
     *
     * @param gameId   요청이 들어온 방
     * @param playerId 요청한 플레이어 (접속 시 확인된 값이 들어온다고 가정)
     * @param payload  요청 본문. playerId와 금액은 서버가 채우므로 들어 있지 않다.
     */
    void handle(String gameId, long playerId, JsonNode payload);
}