package com.sparta.ourmarbleserver.lobby.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sparta.ourmarbleserver.config.DocExamples;
import com.sparta.ourmarbleserver.global.protocol.ErrorPayload;
import com.sparta.ourmarbleserver.lobby.dto.GameStartedPayload;
import com.sparta.ourmarbleserver.lobby.dto.RoomConfigRequestPayload;
import com.sparta.ourmarbleserver.lobby.dto.RoomUpdatedPayload;
import com.sparta.ourmarbleserver.lobby.dto.SetReadyRequestPayload;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Redoc 문서 전용 컨트롤러. 대기실(방장은 방 설정, 참가자는 준비)에서 쓰는 요청과 알림을 문서에 보여 주려고 만들었다.
 * 한 페이지에 요청(오른쪽 위 예시)과 그 결과 알림·오류(응답 예시)를 함께 보여 준다. 요청 본문과 알림 필드는 각 dto에서 자동으로 읽고,
 * 예시 JSON은 DocExamples에 있다. 실제 로직과 무관하고, 호출해도 아무 일도 일어나지 않는다.
 */
@Tag(name = "대기실", description = "대기실에 입장한 뒤 방장은 방 설정을 하고 참가자는 준비를 누르는 화면의 메시지")
@RestController
@RequestMapping("/docs")
public class RoomDocController {

    @Operation(summary = "방상태변경", description = "요청 CREATE_ROOM, ENTER_ROOM, LEAVE_ROOM, SET_READY, ROOM_CONFIG_REQUEST → 알림 ROOM_UPDATED. 대기실 상태가 바뀌었다 (입장, 퇴장, 준비, 방장 변경, 설정 변경). 대기실 전원에게 간다. 연결이 끊겨 대기실에서 퇴장 처리될 때도 남은 사람에게 간다. 접속 기록이 60초 넘게 없는 사람을 서버가 주기적으로 내보낼 때도 남은 사람에게 간다. 게임이 끝나 방이 대기실로 돌아올 때도 방 전원에게 간다. 이때 방장을 뺀 사람의 준비는 풀려 있고, 게임 중에 연결이 끊긴 사람은 이미 빠져 있다. 게임 중이던 클라는 이 알림으로 대기실 화면으로 돌아온다. players의 순서가 참가 순서이고 게임 시작 시 턴 순서가 된다. 방장은 항상 ready=true다. 봇은 bot=true이고 playerId가 음수이며 항상 ready=true다. players[].portraitId는 그 사람이 고른 초상화 번호(0~7)이고, 고르지 않았거나 봇이면 빠진다. 누군가 초상화를 바꾸면(SET_PORTRAIT) 대기 중인 방 전원에게 이 알림이 다시 간다. maxPlayers는 최대 인원(2~4), config는 방장이 정한 게임 설정이다. 방장이 설정을 바꾸면(ROOM_CONFIG_REQUEST) 대기실 전원에게 새 값이 담긴 이 알림이 간다. status는 WAITING, PLAYING")
    @ApiResponse(responseCode = "200", description = "서버가 보내는 알림",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoomUpdatedPayload.class),
                    examples = @ExampleObject(name = "ROOM_UPDATED", summary = "ROOM_UPDATED", value = DocExamples.ROOM_UPDATED)))
    @GetMapping("/notifications/room-updated")
    public void roomUpdated() {
    }

    @Operation(summary = "방나가기", description = "type: LEAVE_ROOM. 대기실에서 나간다. 요청 본문은 없다. 방장이 나가면 가장 먼저 참가한 사람(봇 제외)이 방장이 되고, 사람이 한 명도 안 남으면(봇만 남아도) 대기실이 삭제된다. 결과는 요청자에게 ROOM_LEFT와 최신 대기실 목록(ROOM_LIST), 내 전적(PLAYER_RECORD), 남은 대기실 전원에게 ROOM_UPDATED 알림으로 간다. 대기실이 삭제되면 ROOM_UPDATED는 가지 않는다.\n\n"
            + "오류: NOT_IN_ROOM (어느 대기실에도 없음) → ROOM_BUSY (방이 다른 요청을 처리 중) → ROOM_ALREADY_STARTED (시작한 방)")
    @ApiResponse(responseCode = "200", description = "**ROOM_LEFT** — 대기실에서 나가는 데 성공했다는 알림으로 요청자에게만 간다. 이어서 최신 대기실 목록(ROOM_LIST)과 내 전적(PLAYER_RECORD)이 간다. payload는 없다.\n\n",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"),
                    examples = @ExampleObject(name = "ROOM_LEFT", summary = "ROOM_LEFT", value = DocExamples.ROOM_LEFT)))
    @ApiResponse(responseCode = "400", description = "거절되면 **LOBBY_ERROR**가 요청자에게만 가고 서버 상태는 바뀌지 않는다. 가능한 오류 코드는 오른쪽 예시에서 고를 수 있다. 메시지 형식이 틀리면 COMMON_ERROR(INVALID_MESSAGE)가 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = {
                            @ExampleObject(name = "NOT_IN_ROOM", summary = "NOT_IN_ROOM", value = DocExamples.LOBBY_NOT_IN_ROOM),
                            @ExampleObject(name = "ROOM_BUSY", summary = "ROOM_BUSY", value = DocExamples.LOBBY_ROOM_BUSY),
                            @ExampleObject(name = "ROOM_ALREADY_STARTED", summary = "ROOM_ALREADY_STARTED", value = DocExamples.LOBBY_ROOM_ALREADY_STARTED),
                            @ExampleObject(name = "INVALID_MESSAGE", summary = "INVALID_MESSAGE", value = DocExamples.COMMON_INVALID_MESSAGE)}))
    @PostMapping("/requests/leave-room")
    public void leaveRoom() {
    }

    @Operation(summary = "준비", description = "type: SET_READY. 대기실에서 준비하거나 준비를 취소한다. ready=true면 준비, false면 취소. 결과는 대기실 전원에게 ROOM_UPDATED 알림으로 간다.\n\n"
            + "오류 (검증 순서): NOT_IN_ROOM (어느 대기실에도 없음) → ROOM_BUSY (방이 다른 요청을 처리 중) → ROOM_ALREADY_STARTED (시작한 방) → INVALID_STATE (방장은 항상 준비 상태라 바꿀 수 없음)")
    @ApiResponse(responseCode = "200", description = "**ROOM_UPDATED** — 대기실 전원에게 새 상태가 간다. 필드와 언제 가는지는 대기실의 \"방상태변경\" 페이지를 본다.")
    @ApiResponse(responseCode = "400", description = "거절되면 **LOBBY_ERROR**가 요청자에게만 가고 서버 상태는 바뀌지 않는다. 가능한 오류 코드는 오른쪽 예시에서 고를 수 있다. 메시지 형식이 틀리면 COMMON_ERROR(INVALID_MESSAGE)가 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = {
                            @ExampleObject(name = "NOT_IN_ROOM", summary = "NOT_IN_ROOM", value = DocExamples.LOBBY_NOT_IN_ROOM),
                            @ExampleObject(name = "ROOM_BUSY", summary = "ROOM_BUSY", value = DocExamples.LOBBY_ROOM_BUSY),
                            @ExampleObject(name = "ROOM_ALREADY_STARTED", summary = "ROOM_ALREADY_STARTED", value = DocExamples.LOBBY_ROOM_ALREADY_STARTED),
                            @ExampleObject(name = "INVALID_STATE", summary = "INVALID_STATE", value = DocExamples.LOBBY_INVALID_STATE),
                            @ExampleObject(name = "INVALID_MESSAGE", summary = "INVALID_MESSAGE", value = DocExamples.COMMON_INVALID_MESSAGE)}))
    @PostMapping("/requests/set-ready")
    public void setReady(@RequestBody SetReadyRequestPayload body) {
    }

    @Operation(summary = "방설정변경", description = "type: ROOM_CONFIG_REQUEST. 방장이 대기실의 게임 설정(config), 최대 인원(maxPlayers), 봇 수(botCount)를 바꾼다. 세 값 모두 필수이고, 빠뜨리거나 형식이 틀리거나 config 값이 범위를 벗어나면 COMMON_ERROR(INVALID_MESSAGE)로 거절된다. 방 만들기 창에서는 CREATE_ROOM 직후에 이 요청으로 맵과 최대 인원을 정한다. 봇이 없으면 botCount는 0이다. 결과는 대기실 전원에게 새 값이 담긴 ROOM_UPDATED 알림으로 간다. 실패하면 대기실은 그대로다.\n\n"
            + "오류 (검증 순서): NOT_IN_ROOM (어느 대기실에도 없음) → ROOM_BUSY (방이 다른 요청을 처리 중) → ROOM_NOT_FOUND (없는 방) → ROOM_ALREADY_STARTED (시작한 방) → NOT_HOST (방장 아님) → INVALID_STATE (설정이 없음) → INVALID_MAX_PLAYERS (최대 인원이 2~4가 아니거나 지금 사람 수보다 적음) → INVALID_BOT_COUNT (봇 수가 음수이거나 사람과 봇을 합친 수가 최대 인원을 넘음)")
    @ApiResponse(responseCode = "200", description = "**ROOM_UPDATED** — 대기실 전원에게 새 상태가 간다. 필드와 언제 가는지는 대기실의 \"방상태변경\" 페이지를 본다.")
    @ApiResponse(responseCode = "400", description = "거절되면 **LOBBY_ERROR**가 요청자에게만 가고 서버 상태는 바뀌지 않는다. 가능한 오류 코드는 오른쪽 예시에서 고를 수 있다. 메시지 형식이 틀리면 COMMON_ERROR(INVALID_MESSAGE)가 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = {
                            @ExampleObject(name = "NOT_IN_ROOM", summary = "NOT_IN_ROOM", value = DocExamples.LOBBY_NOT_IN_ROOM),
                            @ExampleObject(name = "ROOM_BUSY", summary = "ROOM_BUSY", value = DocExamples.LOBBY_ROOM_BUSY),
                            @ExampleObject(name = "ROOM_NOT_FOUND", summary = "ROOM_NOT_FOUND", value = DocExamples.LOBBY_ROOM_NOT_FOUND),
                            @ExampleObject(name = "ROOM_ALREADY_STARTED", summary = "ROOM_ALREADY_STARTED", value = DocExamples.LOBBY_ROOM_ALREADY_STARTED),
                            @ExampleObject(name = "NOT_HOST", summary = "NOT_HOST", value = DocExamples.LOBBY_NOT_HOST),
                            @ExampleObject(name = "INVALID_STATE", summary = "INVALID_STATE", value = DocExamples.LOBBY_INVALID_STATE),
                            @ExampleObject(name = "INVALID_MAX_PLAYERS", summary = "INVALID_MAX_PLAYERS", value = DocExamples.LOBBY_INVALID_MAX_PLAYERS),
                            @ExampleObject(name = "INVALID_BOT_COUNT", summary = "INVALID_BOT_COUNT", value = DocExamples.LOBBY_INVALID_BOT_COUNT),
                            @ExampleObject(name = "INVALID_MESSAGE", summary = "INVALID_MESSAGE", value = DocExamples.COMMON_INVALID_MESSAGE)}))
    @PostMapping("/requests/room-config-request")
    public void roomConfigRequest(@RequestBody RoomConfigRequestPayload body) {
    }

    @Operation(summary = "게임시작", description = "type: START_GAME. 방장이 게임을 시작한다. 요청 본문은 없다. 결과는 방 전원에게 GAME_STARTED 알림으로 간다.\n\n"
            + "오류 (검증 순서): NOT_IN_ROOM (어느 대기실에도 없음) → ROOM_BUSY (방이 다른 요청을 처리 중) → ROOM_ALREADY_STARTED (이미 시작함) → NOT_HOST (방장 아님) → NOT_ENOUGH_PLAYERS (2명 미만) → NOT_ALL_READY (준비 안 한 사람 있음)")
    @ApiResponse(responseCode = "200", description = "**GAME_STARTED** — 게임이 시작됐다. 방 전원에게 간다. 클라는 이 알림으로 게임 화면으로 넘어간다. 접속 직후 게임 중인 방이 있을 때도 접속한 사람에게 간다",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = GameStartedPayload.class),
                    examples = @ExampleObject(name = "GAME_STARTED", summary = "GAME_STARTED", value = DocExamples.GAME_STARTED)))
    @ApiResponse(responseCode = "400", description = "거절되면 **LOBBY_ERROR**가 요청자에게만 가고 서버 상태는 바뀌지 않는다. 가능한 오류 코드는 오른쪽 예시에서 고를 수 있다. 메시지 형식이 틀리면 COMMON_ERROR(INVALID_MESSAGE)가 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = {
                            @ExampleObject(name = "NOT_IN_ROOM", summary = "NOT_IN_ROOM", value = DocExamples.LOBBY_NOT_IN_ROOM),
                            @ExampleObject(name = "ROOM_BUSY", summary = "ROOM_BUSY", value = DocExamples.LOBBY_ROOM_BUSY),
                            @ExampleObject(name = "ROOM_ALREADY_STARTED", summary = "ROOM_ALREADY_STARTED", value = DocExamples.LOBBY_ROOM_ALREADY_STARTED),
                            @ExampleObject(name = "NOT_HOST", summary = "NOT_HOST", value = DocExamples.LOBBY_NOT_HOST),
                            @ExampleObject(name = "NOT_ENOUGH_PLAYERS", summary = "NOT_ENOUGH_PLAYERS", value = DocExamples.LOBBY_NOT_ENOUGH_PLAYERS),
                            @ExampleObject(name = "NOT_ALL_READY", summary = "NOT_ALL_READY", value = DocExamples.LOBBY_NOT_ALL_READY),
                            @ExampleObject(name = "INVALID_MESSAGE", summary = "INVALID_MESSAGE", value = DocExamples.COMMON_INVALID_MESSAGE)}))
    @PostMapping("/requests/start-game")
    public void startGame() {
    }
}