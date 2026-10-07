package com.sparta.ourmarbleserver.auth.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MemberResponse {
    private final Long id;
    private final String email;
}