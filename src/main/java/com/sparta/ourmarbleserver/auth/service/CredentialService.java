package com.sparta.ourmarbleserver.auth.service;

import com.sparta.ourmarbleserver.auth.dto.LoginRequest;
import com.sparta.ourmarbleserver.auth.entity.Player;
import com.sparta.ourmarbleserver.auth.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        String password = request.getPassword();
        String encodedPassword = player.getPassword();
        if (!passwordEncoder.matches(password, encodedPassword)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return player;
    }
}

