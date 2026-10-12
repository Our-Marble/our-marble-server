package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageRouterTest {

    static class FakeHandler implements WsMessageHandler {
        final Set<MessageType> types;
        final List<MessageType> received = new ArrayList<>();
        FakeHandler(MessageType... types) { this.types = Set.of(types); }
        @Override public Set<MessageType> types() { return types; }
        @Override public void handle(MessageType type, long playerId, JsonNode payload) { received.add(type); }
    }

    private final JsonNode emptyBody = JsonNodeFactory.instance.objectNode();

    @Test
    void type에_맞는_핸들러로_넘긴다() {
        FakeHandler game = new FakeHandler(MessageType.ROLL_DICE);
        FakeHandler lobby = new FakeHandler(MessageType.CREATE_ROOM);
        MessageRouter router = new MessageRouter(List.of(game, lobby));

        router.route(1L, new InboundMessage(MessageType.ROLL_DICE, emptyBody));

        assertThat(game.received).containsExactly(MessageType.ROLL_DICE);
        assertThat(lobby.received).isEmpty();
    }

    @Test
    void 맡은_핸들러가_없으면_INVALID_MESSAGE() {
        MessageRouter router = new MessageRouter(List.of(new FakeHandler(MessageType.ROLL_DICE)));

        assertThatThrownBy(() -> router.route(1L, new InboundMessage(MessageType.CREATE_ROOM, emptyBody)))
                .isInstanceOf(GameException.class)
                .extracting(e -> ((GameException) e).code())
                .isEqualTo(ErrorCode.INVALID_MESSAGE);
    }

    @Test
    void 같은_type을_두_핸들러가_맡으면_시작_실패() {
        assertThatThrownBy(() -> new MessageRouter(List.of(
                new FakeHandler(MessageType.ROLL_DICE),
                new FakeHandler(MessageType.ROLL_DICE))))
                .isInstanceOf(IllegalStateException.class);
    }
}