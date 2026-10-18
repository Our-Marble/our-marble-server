package com.sparta.ourmarbleserver.auth.service;

import com.sparta.ourmarbleserver.auth.dto.LoginRequest;
import com.sparta.ourmarbleserver.auth.dto.PlayerResponse;
import com.sparta.ourmarbleserver.auth.dto.SignupRequest;
import com.sparta.ourmarbleserver.auth.entity.Player;
import com.sparta.ourmarbleserver.auth.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final PlayerRepository playerRepository;

    @Transactional
    public PlayerResponse signup(SignupRequest request) {
        Player player = playerRepository.save(
                new Player(request.getEmail(), request.getPassword(), request.getNickname())
        );
        return toResponse(player);
    }

    @Transactional(readOnly = true)
    public PlayerResponse authenticate(LoginRequest request) {
        Player player = playerRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!player.getPassword().equals(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return toResponse(player);
    }

    @Transactional(readOnly = true)
    public PlayerResponse requireMember(Long memberId) {
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        Player player = playerRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return toResponse(player);
    }

    private PlayerResponse toResponse(Player player) {
        return new PlayerResponse(
                player.getId(),
                player.getEmail(),
                player.getPlayRecord().getNickname()
        );
    }
}
