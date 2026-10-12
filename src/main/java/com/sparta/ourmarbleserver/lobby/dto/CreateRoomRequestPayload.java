package com.sparta.ourmarbleserver.lobby.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** CREATE_ROOM 요청: 방을 만든다. 만든 사람이 방장이 된다. */
public record CreateRoomRequestPayload(
        @Schema(description = "맵 번호", example = "1") int mapId) {
}