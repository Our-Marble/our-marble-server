package com.sparta.ourmarbleserver.lobby.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** SET_READY 요청: 준비하거나 준비를 취소한다. */
public record SetReadyRequestPayload(
        @Schema(description = "true면 준비, false면 준비 취소", example = "true") boolean ready) {
}