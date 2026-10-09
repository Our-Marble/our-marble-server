package com.sparta.ourmarbleserver.game.dto;

import com.sparta.ourmarbleserver.global.protocol.MessageType;

/** 클라이언트로 보낼 알림 하나. 종류와 내용만 가지고, 실제로 보내는 일은 요청을 부른 쪽(MessageRouter)이 한다. */
public record GameMessage(MessageType type, Object payload) {
}