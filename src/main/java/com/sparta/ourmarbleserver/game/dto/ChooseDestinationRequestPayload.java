package com.sparta.ourmarbleserver.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** CHOOSE_DESTINATION 요청: 세계여행 칸에서 시작한 턴에 목적지를 고른다. 월급 없이 이동한다. */
public record ChooseDestinationRequestPayload(
        @Schema(description = "목적지 칸 번호 (0~31)", example = "31") int destinationPosition) {
}