package com.sparta.ourmarbleserver.lobby.dto;

import com.sparta.ourmarbleserver.game.state.GameConfig;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ROOM_CONFIG_REQUEST 요청: 방장이 게임 설정과 최대 인원을 바꾼다.
 * config 값의 범위는 GameConfig를 만들 때 검증되고, 벗어나면 INVALID_MESSAGE로 거절된다.
 * maxPlayers가 2~4가 아니거나 지금 방 인원보다 적으면 INVALID_MAX_PLAYERS. (빠뜨리면 0이라 거절된다)
 */
public record RoomConfigRequestPayload(
        @Schema(description = "바꿀 게임 설정") GameConfig config,
        @Schema(description = "최대 인원 (2~4, 지금 방 인원 이상)", example = "4") int maxPlayers) {
}