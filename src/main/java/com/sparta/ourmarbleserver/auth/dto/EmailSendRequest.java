package com.sparta.ourmarbleserver.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class EmailSendRequest {

    @NotBlank
    @Email
    @Schema(description = "인증번호를 받을 이메일", example = "player@example.com")
    private String email;
}
