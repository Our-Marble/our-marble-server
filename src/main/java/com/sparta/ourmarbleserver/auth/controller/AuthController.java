package com.sparta.ourmarbleserver.auth.controller;

import com.sparta.ourmarbleserver.auth.dto.AccessTokenResponse;
import com.sparta.ourmarbleserver.auth.dto.LoginRequest;
import com.sparta.ourmarbleserver.auth.dto.PlayerResponse;
import com.sparta.ourmarbleserver.auth.dto.SignupRequest;
import com.sparta.ourmarbleserver.auth.service.JwtAuthService;
import com.sparta.ourmarbleserver.auth.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController()
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final JwtAuthService jwtAuthService;
    private final PlayerService playerService;

    @PostMapping("/signup")
    public ResponseEntity<PlayerResponse> signup(
            @Valid @RequestBody SignupRequest body
    ) {
        PlayerResponse player = playerService.signup(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(player);
    }

    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(
            @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(jwtAuthService.login(request));
    }

    //형식상 만들어 놓음 실제 기능은 없음
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
