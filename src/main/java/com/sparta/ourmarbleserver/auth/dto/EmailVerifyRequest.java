package com.sparta.ourmarbleserver.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class EmailVerifyRequest {

    @NotBlank
    @Email
    @Schema(description = "인증번호를 받은 이메일", example = "player@example.com")
    private String email;

    @NotBlank
    @Pattern(regexp = "\\d{6}")
    @Schema(description = "이메일로 받은 6자리 인증번호", example = "483921")
    private String code;
}
