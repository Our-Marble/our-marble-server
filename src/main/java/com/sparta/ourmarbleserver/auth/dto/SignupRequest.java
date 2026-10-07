package com.sparta.ourmarbleserver.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SignupRequest {
    private String email;
    private String password;

    @NotBlank
    @Size(max = 20)
    private String nickname;
}
