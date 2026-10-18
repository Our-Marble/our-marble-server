package com.sparta.ourmarbleserver.game.dto;

import java.util.List;

import com.sparta.ourmarbleserver.global.protocol.MessageType;

/**
 * 요청 하나를 처리한 결과로 보낼 알림 목록. GameService는 알림을 직접 보내지 않고 이것을 돌려주고,
 * 요청을 부른 쪽(MessageRouter)이 roomId의 방 전원에게 messages를 순서대로 보낸다.
 * 보낼 알림이 없으면 messages가 비어 있다.
 */
public record GameResult(String roomId, List<GameMessage> messages) {

    /** 보낼 알림이 없는 결과 */
    public static GameResult none(String roomId) {
        return new GameResult(roomId, List.of());
    }

    /** 알림 하나를 보내는 결과 */
    public static GameResult of(String roomId, MessageType type, Object payload) {
        return new GameResult(roomId, List.of(new GameMessage(type, payload)));
    }

    /** 알림 종류만 순서대로. (로그와 테스트용) */
    public List<MessageType> types() {
        return messages.stream().map(GameMessage::type).toList();
    }

    /** 해당 종류의 알림 내용만 꺼낸다. (테스트용) */
    public <T> List<T> payloadsOf(MessageType type, Class<T> payloadClass) {
        return messages.stream()
                .filter(message -> message.type() == type)
                .map(message -> payloadClass.cast(message.payload()))
                .toList();
    }
}