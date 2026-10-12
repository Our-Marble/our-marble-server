package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 요청의 type을 보고 처리할 WsMessageHandler를 골라 넘긴다.
 * 시작할 때 모든 핸들러의 types()를 모아 표를 만들고, 같은 type을 두 핸들러가 맡으면 앱이 뜨지 않게 막는다.
 */
@Component
public class MessageRouter {

    private final Map<MessageType, WsMessageHandler> handlers = new EnumMap<>(MessageType.class);

    public MessageRouter(List<WsMessageHandler> handlerList) {
        for (WsMessageHandler handler : handlerList) {
            for (MessageType type : handler.types()) {
                WsMessageHandler previous = handlers.put(type, handler);
                if (previous != null) {
                    throw new IllegalStateException("type " + type + "을 두 핸들러가 맡고 있습니다: "
                            + previous.getClass().getSimpleName() + ", " + handler.getClass().getSimpleName());
                }
            }
        }
    }

    // @throws GameException 맡은 핸들러가 없는 type이면 INVALID_MESSAGE. 핸들러가 던진 GameException은 그대로 올라간다.
    public void route(long playerId, InboundMessage message) {
        WsMessageHandler handler = handlers.get(message.type());
        if (handler == null) {
            throw new GameException(ErrorCode.INVALID_MESSAGE);
        }
        handler.handle(message.type(), playerId, message.body());
    }
}