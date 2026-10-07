package com.sparta.ourmarbleserver.auth.service;

import com.sparta.ourmarbleserver.auth.dto.PlayerResponse;
import com.sparta.ourmarbleserver.auth.dto.SignupRequest;
import com.sparta.ourmarbleserver.auth.entity.Player;
import com.sparta.ourmarbleserver.auth.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlayerService {
    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public PlayerResponse signup(
            SignupRequest request
    ) {
        String password = request.getPassword();
        String encodedPassword = passwordEncoder.encode(password);
        Player player = new Player(request.getEmail(), encodedPassword, request.getNickname());
        Player savedUser = playerRepository.save(player);
        return new PlayerResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getPlayRecord().getNickname());
    }

    @Transactional(readOnly = true)
    public PlayerResponse findMe(
            long userId
    ) {
        Player player = playerRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new PlayerResponse(player.getId(), player.getEmail(), player.getPlayRecord().getNickname());
    }

    @Transactional(readOnly = true)
    public List<PlayerResponse> findAll() {
        List<Player> players = playerRepository.findAll();
        return players.stream()
                .map(player -> new PlayerResponse(player.getId(), player.getEmail(),player.getPlayRecord().getNickname()))
                .toList();
    }
}
