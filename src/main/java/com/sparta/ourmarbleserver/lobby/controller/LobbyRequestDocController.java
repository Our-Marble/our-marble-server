package com.sparta.ourmarbleserver.lobby.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sparta.ourmarbleserver.lobby.dto.ChatSendRequestPayload;
import com.sparta.ourmarbleserver.lobby.dto.EnterRoomRequestPayload;
import com.sparta.ourmarbleserver.lobby.dto.RoomConfigRequestPayload;
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
            "응답 없음. 성공하면 결과 알림이 간다. 실패하면 LOBBY_ERROR가 요청자에게만 간다. (메시지 형식이 틀리면 COMMON_ERROR)";

    @Operation(summary = "방만들기", description = "type: CREATE_ROOM. 대기실을 만든다. 요청 본문은 없다. 맵과 최대 인원은 이 요청에 없다. 만들 때는 기본 맵으로 정해지고, 클라가 이어서 보내는 ROOM_CONFIG_REQUEST로 맵과 최대 인원을 정한다. 만든 사람이 방장이 되고 자동으로 참가한다. 결과는 요청자에게 ROOM_ENTERED, 대기실 전원에게 ROOM_UPDATED 알림으로 간다.\n\n"
            + "오류: ALREADY_IN_ROOM (이미 어떤 방에 있음, 시작한 방 포함)")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/create-room")
    public void createRoom() {
    }

    @Operation(summary = "방입장", description = "type: ENTER_ROOM. 대기실에 입장한다. roomId는 대기실 목록(ROOM_LIST)에서 고른 방 번호이거나, 코드로 입장할 때 입력한 방 번호다. 결과는 요청자에게 ROOM_ENTERED, 대기실 전원에게 ROOM_UPDATED 알림으로 간다.\n\n"
            + "오류 (검증 순서): ROOM_NOT_FOUND (없는 방) → ROOM_ALREADY_STARTED (시작한 방) → ALREADY_IN_ROOM (이미 어떤 방에 있음) → ROOM_FULL (가득 참)")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/enter-room")
    public void enterRoom(@RequestBody EnterRoomRequestPayload body) {
    }

    @Operation(summary = "방나가기", description = "type: LEAVE_ROOM. 대기실에서 나간다. 요청 본문은 없다. 방장이 나가면 가장 먼저 참가한 사람(봇 제외)이 방장이 되고, 사람이 한 명도 안 남으면(봇만 남아도) 대기실이 삭제된다. 결과는 요청자에게 ROOM_LEFT와 최신 대기실 목록(ROOM_LIST), 내 전적(PLAYER_RECORD), 남은 대기실 전원에게 ROOM_UPDATED 알림으로 간다. 대기실이 삭제되면 ROOM_UPDATED는 가지 않는다.\n\n"
            + "오류: NOT_IN_ROOM (어느 대기실에도 없음) → ROOM_ALREADY_STARTED (시작한 방)")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/leave-room")
    public void leaveRoom() {
    }

    @Operation(summary = "준비", description = "type: SET_READY. 대기실에서 준비하거나 준비를 취소한다. ready=true면 준비, false면 취소. 결과는 대기실 전원에게 ROOM_UPDATED 알림으로 간다.\n\n"
            + "오류 (검증 순서): NOT_IN_ROOM (어느 대기실에도 없음) → ROOM_ALREADY_STARTED (시작한 방) → INVALID_STATE (방장은 항상 준비 상태라 바꿀 수 없음)")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/set-ready")
    public void setReady(@RequestBody SetReadyRequestPayload body) {
    }

    @Operation(summary = "게임시작", description = "type: START_GAME. 방장이 게임을 시작한다. 요청 본문은 없다. 결과는 방 전원에게 GAME_STARTED 알림으로 간다.\n\n"
            + "오류 (검증 순서): NOT_IN_ROOM (어느 대기실에도 없음) → ROOM_ALREADY_STARTED (이미 시작함) → NOT_HOST (방장 아님) → NOT_ENOUGH_PLAYERS (2명 미만) → NOT_ALL_READY (준비 안 한 사람 있음)")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/start-game")
    public void startGame() {
    }

    @Operation(summary = "방목록새로고침", description = "type: ROOM_LIST_REQUEST. 대기실 목록을 다시 요청한다. 요청 본문은 없다. 결과는 ROOM_LIST와 내 전적(PLAYER_RECORD) 알림이 요청자에게만 간다.")
    @ApiResponse(responseCode = "200", description = "응답 없음. 대기실 목록(ROOM_LIST)과 내 전적(PLAYER_RECORD)이 요청자에게만 간다.")
    @PostMapping("/room-list-request")
    public void roomListRequest() {
    }

    @Operation(summary = "방설정변경", description = "type: ROOM_CONFIG_REQUEST. 방장이 대기실의 게임 설정(config), 최대 인원(maxPlayers), 봇 수(botCount)를 바꾼다. 세 값 모두 필수이고, 빠뜨리거나 형식이 틀리거나 config 값이 범위를 벗어나면 COMMON_ERROR(INVALID_MESSAGE)로 거절된다. 방 만들기 창에서는 CREATE_ROOM 직후에 이 요청으로 맵과 최대 인원을 정한다. 봇이 없으면 botCount는 0이다. 결과는 대기실 전원에게 새 값이 담긴 ROOM_UPDATED 알림으로 간다. 실패하면 대기실은 그대로다.\n\n"
            + "오류 (검증 순서): NOT_IN_ROOM (어느 대기실에도 없음) → ROOM_NOT_FOUND (없는 방) → ROOM_ALREADY_STARTED (시작한 방) → NOT_HOST (방장 아님) → INVALID_STATE (설정이 없음) → INVALID_MAX_PLAYERS (최대 인원이 2~4가 아니거나 지금 사람 수보다 적음) → INVALID_BOT_COUNT (봇 수가 음수이거나 사람과 봇을 합친 수가 최대 인원을 넘음)")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/room-config-request")
    public void roomConfigRequest(@RequestBody RoomConfigRequestPayload body) {
    }

    @Operation(summary = "채팅보내기", description = "type: CHAT_SEND. 내가 들어 있는 방에 채팅을 보낸다. 방 번호는 서버가 보낸 사람의 위치로 안다. 결과는 보낸 본인을 포함한 방 전원에게 CHAT_BROADCAST 알림으로 간다. content가 빈 문자열이거나 공백뿐이면 아무 일도 일어나지 않는다. 글자 수 제한과 도배 방지는 서버에서 하지 않는다.\n\n"
            + "오류: COMMON_ERROR(INVALID_MESSAGE) (content가 없거나 문자열이 아님) → NOT_IN_ROOM (어느 방에도 없음) → INVALID_STATE (게임이 시작된 방)")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/chat-send")
    public void chatSend(@RequestBody ChatSendRequestPayload body) {
    }
}
