package com.sparta.ourmarbleserver.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class LoginRequest {
    @Schema(description = "가입한 이메일", example = "player@example.com")
    private String email;
    @Schema(description = "비밀번호", example = "password1234")
    private String password;
}