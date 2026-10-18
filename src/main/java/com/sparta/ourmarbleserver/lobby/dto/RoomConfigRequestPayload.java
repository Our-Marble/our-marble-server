package com.sparta.ourmarbleserver.lobby.dto;

import com.sparta.ourmarbleserver.game.state.GameConfig;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ROOM_CONFIG_REQUEST 요청: 방장이 게임 설정, 최대 인원, 봇 수를 바꾼다. 세 값 모두 필수다.
 * config 값의 범위는 GameConfig를 만들 때 검증되고, 벗어나면 INVALID_MESSAGE로 거절된다.
 * maxPlayers나 botCount를 빠뜨리면 int에 null을 넣을 수 없어 INVALID_MESSAGE로 거절된다. (방은 그대로)
 * maxPlayers가 2~4가 아니거나 지금 사람 수보다 적으면 INVALID_MAX_PLAYERS.
 * botCount가 음수이거나 사람 수 + botCount가 maxPlayers를 넘으면 INVALID_BOT_COUNT.
 */
public record RoomConfigRequestPayload(
        @Schema(description = "바꿀 게임 설정") GameConfig config,
        @Schema(description = "최대 인원 (2~4, 지금 사람 수 이상)", example = "4") int maxPlayers,
        @Schema(description = "방에 있을 봇의 수 (0 이상, 사람 수 + 봇 수는 최대 인원 이하)", example = "1") int botCount) {
}