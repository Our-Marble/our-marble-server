package com.sparta.ourmarbleserver.auth.service;

import com.sparta.ourmarbleserver.auth.dto.AccessTokenResponse;
import com.sparta.ourmarbleserver.auth.dto.LoginRequest;
import com.sparta.ourmarbleserver.config.JwtProvider;
import com.sparta.ourmarbleserver.auth.entity.Player;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JwtAuthService {
    private final CredentialService credentialService;
    private final JwtProvider jwtProvider;

    @Transactional(readOnly = true)
    public AccessTokenResponse login(
            LoginRequest request
    ) {
        Player player = credentialService.authenticate(request);
        String accessToken = jwtProvider.createAccessToken(player);
        return new AccessTokenResponse(accessToken);
    }
}
