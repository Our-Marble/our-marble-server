package com.sparta.ourmarbleserver.global.transport;

import java.util.ArrayList;
import java.util.List;

import com.sparta.ourmarbleserver.global.protocol.MessageType;

/**
 * 테스트용 EventPublisher. 실제로 보내지 않고, 서비스가 내보낸 알림을 순서대로 기록한다.
 * 네트워크 담당의 실제 구현이 없어도 서비스가 어떤 알림을 어떤 순서로 보내는지 검증할 수 있다.
 */
public class FakeEventPublisher implements EventPublisher {

    /** 보낸 알림 한 건. playerId가 null이면 방 전원에게 보낸 것이다. */
    public record Sent(String roomId, Long playerId, MessageType type, Object payload) {
    }

    private final List<Sent> sent = new ArrayList<>();

    @Override
    public void publishToRoom(String roomId, MessageType type, Object payload) {
        record(new Sent(roomId, null, type, payload));
    }

    @Override
    public void sendToPlayer(String roomId, long playerId, MessageType type, Object payload) {
        record(new Sent(roomId, playerId, type, payload));
    }

    /** 보낸 순서대로 전체 알림 */
    public List<Sent> events() {
        return List.copyOf(sent);
    }

    /** 보낸 알림의 type 순서만 (흐름 검증용: DICE_ROLLED → PROPERTY_PURCHASED 등) */
    public List<MessageType> types() {
        return sent.stream().map(Sent::type).toList();
    }

    /** 해당 type의 payload만 꺼낸다. */
    public <T> List<T> payloadsOf(MessageType type, Class<T> payloadClass) {
        return sent.stream()
                .filter(s -> s.type() == type)
                .map(s -> payloadClass.cast(s.payload()))
                .toList();
    }

    /** 마지막 알림 */
    public Sent last() {
        return sent.isEmpty() ? null : sent.get(sent.size() - 1);
    }

    public void clear() {
        sent.clear();
    }

    private void record(Sent event) {
        sent.add(event);
        String target = event.playerId() == null ? "방 전원" : "플레이어 " + event.playerId();
        System.out.println("[FakeEventPublisher] " + target + " <- " + event.type() + " " + event.payload());
    }
}