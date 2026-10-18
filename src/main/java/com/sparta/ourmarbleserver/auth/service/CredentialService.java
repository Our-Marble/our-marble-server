package com.sparta.ourmarbleserver.auth.service;

import com.sparta.ourmarbleserver.auth.dto.LoginRequest;
import com.sparta.ourmarbleserver.auth.entity.Player;
import com.sparta.ourmarbleserver.auth.repository.PlayerRepository;
import com.sparta.ourmarbleserver.global.exception.AuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CredentialService {
    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Player authenticate(
            LoginRequest request
    ) {
        Player player = playerRepository.findByEmail(request.getEmail())
                .orElseThrow(this::invalidCredentials);
        String password = request.getPassword();
        String encodedPassword = player.getPassword();
        if (!passwordEncoder.matches(password, encodedPassword)) {
            throw invalidCredentials();
        }
        return player;
    }

    private AuthException invalidCredentials() {
        return new AuthException(
                HttpStatus.UNAUTHORIZED,
                "이메일 또는 비밀번호가 올바르지 않습니다."
        );
    }
}

