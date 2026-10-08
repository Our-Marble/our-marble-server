package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.protocol.MessageTopic;
import com.sparta.ourmarbleserver.global.protocol.MessageType;

import tools.jackson.databind.JsonNode;

/**
 * 클라이언트 요청을 해석한 결과.
 *
 * @param topic 메시지 종류 (LOBBY / GAME)
 * @param type  요청 이름
 * @param body  topic, type을 뺀 나머지 필드 (예: propertyId, isAccept)
 */
public record InboundMessage(MessageTopic topic, MessageType type, JsonNode body) {
}
