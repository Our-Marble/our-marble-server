package com.sparta.ourmarbleserver.auth.service;

import com.sparta.ourmarbleserver.auth.dto.PlayerResponse;
import com.sparta.ourmarbleserver.auth.dto.SignupRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SignupService {

    private final EmailVerificationService emailVerificationService;
    private final PlayerService playerService;

    public PlayerResponse signup(SignupRequest request) {
        emailVerificationService.requireVerified(request.getEmail());

        PlayerResponse player = playerService.signup(request);

        emailVerificationService.deleteVerification(request.getEmail());
        return player;
    }
}
