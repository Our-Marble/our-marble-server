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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "플레이어", description = "로그인한 내 정보와 전적. 모든 요청에 Authorization: Bearer {accessToken} 헤더가 필요하다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/players")
@RequiredArgsConstructor
public class PlayerController {
    private final PlayerService playerService;

    @Operation(summary = "내 정보", description = "토큰의 주인(로그인한 나)의 회원 정보")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "401", description = "토큰이 없거나, 틀렸거나, 만료됨")
    @GetMapping("/me")
    public ResponseEntity<PlayerResponse> me(
            @AuthenticationPrincipal AuthPlayer authPlayer
    ) {
        return ResponseEntity.ok(playerService.findMe(authPlayer.playerId()));
    }

    @Operation(summary = "내 전적 조회", description = "닉네임, 플레이한 판 수, 이긴 판 수")
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "401", description = "토큰이 없거나, 틀렸거나, 만료됨")
    @GetMapping("/me/play-record")
    public ResponseEntity<PlayRecordResponse> playRecord(
            @AuthenticationPrincipal AuthPlayer authPlayer
    ) {
        return ResponseEntity.ok(playerService.findMyPlayRecord(authPlayer.playerId()));
    }

    @Operation(summary = "내 전적 기록", description = "한 판을 마친 결과를 기록한다. 플레이한 판 수가 1 늘고, won이 true이면 이긴 판 수도 1 는다.")
    @ApiResponse(responseCode = "200", description = "성공. 갱신된 전적을 돌려준다.")
    @ApiResponse(responseCode = "400", description = "won 값이 없음")
    @ApiResponse(responseCode = "401", description = "토큰이 없거나, 틀렸거나, 만료됨")
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

    @Operation(summary = "내 전적 초기화 (관리자 전용)", description = "판 수와 이긴 판 수를 0으로 되돌린다. ADMIN 권한이 있는 계정만 호출할 수 있다.")
    @ApiResponse(responseCode = "200", description = "성공. 초기화된 전적을 돌려준다.")
    @ApiResponse(responseCode = "401", description = "토큰이 없거나, 틀렸거나, 만료됨")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한이 아님")
    @PreAuthorize("hasRole('ADMIN')") //테스트용으로 하나 넣어 보았습니다
    @DeleteMapping("/me/play-record")
    public ResponseEntity<PlayRecordResponse> resetPlayRecord(
            @AuthenticationPrincipal AuthPlayer authPlayer
    ) {
        return ResponseEntity.ok(playerService.resetPlayRecord(authPlayer.playerId()));
    }
}
