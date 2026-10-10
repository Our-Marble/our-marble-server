package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.protocol.MessageType;
import tools.jackson.databind.JsonNode;

/**
 * 클라이언트 요청을 해석한 결과.
 *
 * @param type 요청 이름. 이 값으로 처리할 핸들러를 고른다.
 * @param body type을 뺀 나머지 필드 (예: propertyId, isAccept)
 */
public record InboundMessage(MessageType type, JsonNode body) {
}