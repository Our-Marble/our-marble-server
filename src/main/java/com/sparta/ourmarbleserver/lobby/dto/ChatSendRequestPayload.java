package com.sparta.ourmarbleserver.lobby.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** CHAT_SEND 요청: 내가 있는 방에 채팅을 보낸다. */
public record ChatSendRequestPayload(
        @Schema(description = "채팅 내용", example = "하이") String content) {
}
