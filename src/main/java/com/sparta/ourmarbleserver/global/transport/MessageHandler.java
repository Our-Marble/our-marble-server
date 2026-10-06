package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.protocol.MessageType;
import tools.jackson.databind.JsonNode;

/**
 * 클라이언트 요청을 받아 처리하는 쪽의 규격.
 * 도메인 서비스가 구현하고, 전송 계층(MessageRouter)이 type()으로 찾아 handle()을 호출한다.
 *
 * 담당하는 요청 종류: ROLL_DICE, CHOOSE_DESTINATION, PURCHASE_PROPERTY, BUILD,
 * ACQUIRE_PROPERTY, SELL_PROPERTIES, DRAW_CARD
 */
public interface MessageHandler {

    /** 이 핸들러가 담당하는 요청 종류. */
    MessageType type();

    /**
     * 요청이 오면 호출된다. 규칙에 어긋난 요청이면 GameException을 던진다.
     * (ERROR 응답 전송은 전송 계층이 맡고, 초기 단계에서는 TODO)
     *
     * @param roomId   요청이 들어온 방
     * @param playerId 요청한 플레이어 (접속 시 확인된 값이 들어온다고 가정)
     * @param payload  요청 본문. propertyId, isAccept, propertyIds 같은 선택값만 들어 있다.
     *                 playerId와 금액(통행료, 매각가, 인수가)은 서버가 채우므로 들어 있지 않다.
     */
    void handle(String roomId, long playerId, JsonNode payload);
}