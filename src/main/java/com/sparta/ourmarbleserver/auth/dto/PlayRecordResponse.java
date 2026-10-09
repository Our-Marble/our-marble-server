package com.sparta.ourmarbleserver.auth.dto;

public record PlayRecordResponse(
        String nickname,
        int gamesPlayed,
        int gamesWon
) {
}
