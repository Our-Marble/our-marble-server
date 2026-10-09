package com.sparta.ourmarbleserver.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PlayerResponse {
    @Schema(description = "플레이어 번호. 게임 메시지의 playerId와 같은 값이다.", example = "1")
    private final Long id;
    @Schema(description = "이메일", example = "player@example.com")
    private final String email;
    @Schema(description = "닉네임", example = "마블왕")
    private final String nickname;
}
