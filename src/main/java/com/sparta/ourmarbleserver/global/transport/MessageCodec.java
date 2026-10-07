package com.sparta.ourmarbleserver.global.transport;

import org.springframework.stereotype.Component;

import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageTopic;
import com.sparta.ourmarbleserver.global.protocol.MessageType;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * WebSocket 메시지와 자바 객체 사이의 변환.
 * 메시지는 payload로 감싸지 않고 필드를 평평하게 둔다.
 * <pre>
 * { "topic": "GAME", "type": "DICE_ROLLED", "playerId": 2, "dice1": 3, "dice2": 6 }
 * </pre>
 * 도메인 서비스는 payload record만 만들고, topic·type은 여기서 붙인다.
 * roomId는 서버가 플레이어 위치로 알고, seq(순번)는 TCP가 한 연결 안의 순서를 보장하므로 두지 않는다.
 */
@Component
public class MessageCodec {

    private static final String TOPIC = "topic";
    private static final String TYPE = "type";

    private final ObjectMapper objectMapper;

    public MessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 클라이언트가 보낸 JSON 문자열을 해석한다.
     *
     * @throws GameException JSON이 아니거나 topic·type이 없거나 모르는 값이면 INVALID_MESSAGE
     */
    public InboundMessage decode(String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new GameException(ErrorCode.INVALID_MESSAGE);
        }
        if (root == null || !root.isObject()) {
            throw new GameException(ErrorCode.INVALID_MESSAGE);
        }

        MessageTopic topic = parseEnum(MessageTopic.class, root.get(TOPIC));
        MessageType type = parseEnum(MessageType.class, root.get(TYPE));

        ObjectNode body = ((ObjectNode) root).deepCopy();
        body.remove(TOPIC);
        body.remove(TYPE);
        return new InboundMessage(topic, type, body);
    }

    /**
     * 서버 알림을 JSON 문자열로 만든다.
     *
     * @param payload 나머지 필드를 담은 record. 필드가 없으면 null
     */
    public String encode(MessageTopic topic, MessageType type, Object payload) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put(TOPIC, topic.name());
        root.put(TYPE, type.name());

        if (payload != null) {
            JsonNode fields = objectMapper.valueToTree(payload);
            if (!fields.isObject()) {
                throw new IllegalArgumentException("payload는 필드를 가진 객체여야 합니다: " + payload.getClass());
            }
            if (fields.has(TOPIC) || fields.has(TYPE)) {
                throw new IllegalArgumentException("payload에 topic/type 필드를 둘 수 없습니다: " + payload.getClass());
            }
            root.setAll((ObjectNode) fields);
        }

        return objectMapper.writeValueAsString(root);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, JsonNode node) {
        if (node == null || !node.isString()) {
            throw new GameException(ErrorCode.INVALID_MESSAGE);
        }
        try {
            return Enum.valueOf(enumType, node.asString());
        } catch (IllegalArgumentException e) {
            throw new GameException(ErrorCode.INVALID_MESSAGE);
        }
    }
}
