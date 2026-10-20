package com.sparta.ourmarbleserver.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "고른 초상화 번호(0~7). 고른 적이 없으면 없다.", example = "3", nullable = true)
    private final Integer portraitId;

    /** 초상화 없이 만든다. (고른 적이 없는 계정) */
    public PlayerResponse(Long id, String email, String nickname) {
        this(id, email, nickname, null);
    }
}
