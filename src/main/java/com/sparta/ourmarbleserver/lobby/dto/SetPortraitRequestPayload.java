package com.sparta.ourmarbleserver.lobby.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** SET_PORTRAIT 요청: 내 초상화 번호를 바꾼다. 번호는 0~7이다. */
public record SetPortraitRequestPayload(
        @Schema(description = "초상화 번호 (0~7)", example = "3") Integer portraitId) {
}
