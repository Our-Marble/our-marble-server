package com.sparta.ourmarbleserver.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PlayRecordResponse(
        @Schema(description = "닉네임", example = "Alice")
        String nickname,
        @Schema(description = "플레이한 판 수", example = "10")
        int gamesPlayed,
        @Schema(description = "이긴 판 수", example = "4")
        int gamesWon
) {
}
