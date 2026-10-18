package com.sparta.ourmarbleserver.lobby.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** ENTER_ROOM 요청: 대기 중인 방에 들어간다. */
public record EnterRoomRequestPayload(
        @Schema(description = "입장할 방 번호", example = "r_3") String roomId) {
}