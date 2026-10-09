package com.sparta.ourmarbleserver.auth.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PlayerResponse {
    private final Long id;
    private final String email;
    private final String nickname;
}
