package com.sparta.ourmarbleserver.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SignupRequest {
    @NotBlank
    @Email
    @Schema(description = "이메일 (로그인 아이디). 이미 가입한 이메일은 쓸 수 없다.", example = "player@example.com")
    private String email;
    @NotBlank
    @Schema(description = "비밀번호", example = "password1234")
    private String password;
    @NotBlank
    @Size(max = 20)
    @Schema(description = "게임에서 보이는 닉네임 (20자 이하)", example = "Alice")
    private String nickname;
}
