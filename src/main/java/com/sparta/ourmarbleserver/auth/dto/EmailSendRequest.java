package com.sparta.ourmarbleserver.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class EmailSendRequest {

    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Schema(description = "인증번호를 받을 이메일", example = "player@example.com")
    private String email;
}
