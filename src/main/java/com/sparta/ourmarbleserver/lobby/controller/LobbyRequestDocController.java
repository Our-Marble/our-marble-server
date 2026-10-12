package com.sparta.ourmarbleserver.lobby.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sparta.ourmarbleserver.lobby.dto.CreateRoomRequestPayload;
import com.sparta.ourmarbleserver.lobby.dto.EnterRoomRequestPayload;
import com.sparta.ourmarbleserver.lobby.dto.SetReadyRequestPayload;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Redoc 문서 전용 컨트롤러. 클라이언트가 WebSocket으로 서버에 보내는 로비 요청의 모양을 문서에 보여 주려고 만들었다.
 * 요청 본문은 각 요청 dto에서 자동으로 읽는다. 실제 로비 로직과 무관하고, 호출해도 아무 일도 일어나지 않는다.
 */
@Tag(name = "로비 요청", description = "클라이언트가 WebSocket으로 서버에 보내는 로비 요청")
@RestController
@RequestMapping("/docs/requests")
public class LobbyRequestDocController {

    private static final String NO_RESPONSE =
            "응답 없음. 성공하면 결과 알림이 방 전원에게 가고, 실패하면 ERROR가 요청자에게만 간다.";

    @Operation(summary = "방만들기", description = "type: CREATE_ROOM. 방을 만든다. 만든 사람이 방장이 되고 자동으로 참가한다. 결과는 ROOM_UPDATED 알림으로 간다.\n\n"
            + "오류: ALREADY_IN_ROOM (이미 어떤 방에 있음, 시작한 방 포함)")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/create-room")
    public void createRoom(@RequestBody CreateRoomRequestPayload body) {
    }

    @Operation(summary = "방입장", description = "type: ENTER_ROOM. 대기 중인 방에 들어간다. 결과는 ROOM_UPDATED 알림으로 간다.\n\n"
            + "오류 (검증 순서): ROOM_NOT_FOUND (없는 방) → ROOM_ALREADY_STARTED (시작한 방) → ALREADY_IN_ROOM (이미 어떤 방에 있음) → ROOM_FULL (가득 참)")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/enter-room")
    public void enterRoom(@RequestBody EnterRoomRequestPayload body) {
    }

    @Operation(summary = "방나가기", description = "type: LEAVE_ROOM. 방에서 나간다. 요청 본문은 없다. 방장이 나가면 가장 먼저 참가한 사람이 방장이 되고, 마지막 사람이 나가면 방이 삭제된다. 결과는 ROOM_UPDATED 알림으로 간다.\n\n"
            + "오류: NOT_IN_ROOM (어느 방에도 없음) → ROOM_ALREADY_STARTED (시작한 방)")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/leave-room")
    public void leaveRoom() {
    }

    @Operation(summary = "준비", description = "type: SET_READY. 준비하거나 준비를 취소한다. ready=true면 준비, false면 취소. 결과는 ROOM_UPDATED 알림으로 간다.\n\n"
            + "오류 (검증 순서): NOT_IN_ROOM (어느 방에도 없음) → ROOM_ALREADY_STARTED (시작한 방) → INVALID_STATE (방장은 항상 준비 상태라 바꿀 수 없음)")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/set-ready")
    public void setReady(@RequestBody SetReadyRequestPayload body) {
    }

    @Operation(summary = "게임시작", description = "type: START_GAME. 방장이 게임을 시작한다. 요청 본문은 없다. 결과는 GAME_STARTED 알림으로 간다.\n\n"
            + "오류 (검증 순서): NOT_IN_ROOM (어느 방에도 없음) → ROOM_ALREADY_STARTED (이미 시작함) → NOT_HOST (방장 아님) → NOT_ENOUGH_PLAYERS (2명 미만) → NOT_ALL_READY (준비 안 한 사람 있음)")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/start-game")
    public void startGame() {
    }
}