package com.sparta.ourmarbleserver.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.Getter;

@Getter
@RequiredArgsConstructor
public class AccessTokenResponse {
    @Schema(description = "JWT. 이후 요청의 Authorization 헤더에 \"Bearer {accessToken}\"으로 보낸다. 30분 뒤 만료된다.", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature")
    private final String accessToken;
}