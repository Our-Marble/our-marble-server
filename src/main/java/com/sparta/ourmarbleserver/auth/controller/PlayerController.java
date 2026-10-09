package com.sparta.ourmarbleserver.auth.controller;

import com.sparta.ourmarbleserver.auth.dto.AuthPlayer;
import com.sparta.ourmarbleserver.auth.dto.PlayRecordResponse;
import com.sparta.ourmarbleserver.auth.dto.PlayRecordUpdateRequest;
import com.sparta.ourmarbleserver.auth.dto.PlayerResponse;
import com.sparta.ourmarbleserver.auth.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/players")
@RequiredArgsConstructor
public class PlayerController {
    private final PlayerService playerService;

    @GetMapping("/me")
    public ResponseEntity<PlayerResponse> me(
            @AuthenticationPrincipal AuthPlayer authPlayer
    ) {
        return ResponseEntity.ok(playerService.findMe(authPlayer.playerId()));
    }

    @GetMapping("/me/play-record")
    public ResponseEntity<PlayRecordResponse> playRecord(
            @AuthenticationPrincipal AuthPlayer authPlayer
    ) {
        return ResponseEntity.ok(playerService.findMyPlayRecord(authPlayer.playerId()));
    }

    @PatchMapping("/me/play-record")
    public ResponseEntity<PlayRecordResponse> updatePlayRecord(
            @AuthenticationPrincipal AuthPlayer authPlayer,
            @Valid @RequestBody PlayRecordUpdateRequest request
    ) {
        PlayRecordResponse playRecord = playerService.updatePlayRecord(
                authPlayer.playerId(),
                request.won()
        );
        return ResponseEntity.ok(playRecord);
    }

    @PreAuthorize("hasRole('ADMIN')") //테스트용으로 하나 넣어 보았습니다
    @DeleteMapping("/me/play-record")
    public ResponseEntity<PlayRecordResponse> resetPlayRecord(
            @AuthenticationPrincipal AuthPlayer authPlayer
    ) {
        return ResponseEntity.ok(playerService.resetPlayRecord(authPlayer.playerId()));
    }
}
