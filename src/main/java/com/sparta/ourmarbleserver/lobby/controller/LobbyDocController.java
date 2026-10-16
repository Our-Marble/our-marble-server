package com.sparta.ourmarbleserver.lobby.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sparta.ourmarbleserver.config.DocExamples;
import com.sparta.ourmarbleserver.global.protocol.ConnectedPayload;
import com.sparta.ourmarbleserver.global.protocol.ErrorPayload;
import com.sparta.ourmarbleserver.lobby.dto.EnterRoomRequestPayload;
import com.sparta.ourmarbleserver.lobby.dto.PlayerRecordPayload;
import com.sparta.ourmarbleserver.lobby.dto.RoomListPayload;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Redoc 문서 전용 컨트롤러. 로그인한 뒤 대기실 목록이 있는 화면에서 쓰는 요청과 알림을 문서에 보여 주려고 만들었다.
 * 한 페이지에 요청(오른쪽 위 예시)과 그 결과 알림·오류(응답 예시)를 함께 보여 준다. 요청 본문과 알림 필드는 각 dto에서 자동으로 읽고,
 * 예시 JSON은 DocExamples에 있다. 실제 로직과 무관하고, 호출해도 아무 일도 일어나지 않는다.
 */
@Tag(name = "로비", description = "로그인한 뒤 대기실 목록이 있는 화면의 메시지. 접속하면 서버가 먼저 접속 완료(CONNECTED), 대기실 목록, 내 전적을 보낸다.")
@RestController
@RequestMapping("/docs")
public class LobbyDocController {

    @Operation(summary = "접속완료", description = "WebSocket 연결 직후 서버가 본인에게만 보낸다. 클라는 playerId를 직접 정하지 않고 이 값을 로컬 플레이어 번호로 저장한다. 이어서 게임 중인 방이 있으면 GAME_STARTED가, 없으면 대기실 목록(ROOM_LIST)과 내 전적(PLAYER_RECORD)이 본인에게 간다. 접속할 때 이미 대기실에 들어가 있는 사람(게임이 끝나 대기실로 돌아온 방에 남아 있던 경우)은 그 대기실에서 나가게 되고, 남은 사람에게 ROOM_UPDATED가 간다")
    @ApiResponse(responseCode = "200", description = "서버가 보내는 알림",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConnectedPayload.class),
                    examples = @ExampleObject(name = "CONNECTED", summary = "CONNECTED", value = DocExamples.CONNECTED)))
    @GetMapping("/notifications/connected")
    public void connected() {
    }

    @Operation(summary = "내전적", description = "ROOM_LIST를 보낼 때마다 바로 뒤에 본인에게만 같이 가는 내 전적이다. 접속 직후, 대기실 목록 요청(ROOM_LIST_REQUEST), 대기실 나가기 직후에 가고, 게임 중인 방이 있어 접속 직후 GAME_STARTED가 가는 경우에는 가지 않는다. 전적을 조회하지 못하면 ROOM_LIST만 가고 이 알림은 가지 않는다. gamesPlayed는 플레이한 판 수, gamesWon은 이긴 판 수다")
    @ApiResponse(responseCode = "200", description = "서버가 보내는 알림",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlayerRecordPayload.class),
                    examples = @ExampleObject(name = "PLAYER_RECORD", summary = "PLAYER_RECORD", value = DocExamples.PLAYER_RECORD)))
    @GetMapping("/notifications/player-record")
    public void playerRecord() {
    }

    @Operation(summary = "방목록새로고침", description = "type: ROOM_LIST_REQUEST. 대기실 목록을 다시 요청한다. 요청 본문은 없다. 결과는 ROOM_LIST와 내 전적(PLAYER_RECORD) 알림이 요청자에게만 간다.")
    @ApiResponse(responseCode = "200", description = "**ROOM_LIST** — 대기실 목록으로, 요청자에게만 간다. 대기실이 없으면 rooms는 빈 배열이다. playerCount는 지금 참가한 인원수(봇 포함), maxPlayers는 최대 인원이다\n\n"
            + "**ROOM_LIST가 가는 때**\n\n"
            + "- 접속 직후: CONNECTED 다음에 간다. 게임 중인 방이 있으면 ROOM_LIST 대신 GAME_STARTED가 간다.\n- 대기실 목록 요청(ROOM_LIST_REQUEST)을 보냈을 때\n- 대기실에서 나간 직후(LEAVE_ROOM): ROOM_LEFT와 함께 간다.\n\n"
            + "세 경우 모두 ROOM_LIST 바로 뒤에 내 전적(PLAYER_RECORD)이 같이 간다. 목록이 바뀌어도 서버가 자동으로 보내지 않으니, 최신 목록은 ROOM_LIST_REQUEST로 받는다.\n\n",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoomListPayload.class),
                    examples = @ExampleObject(name = "ROOM_LIST", summary = "ROOM_LIST", value = DocExamples.ROOM_LIST)))
    @ApiResponse(responseCode = "400", description = "이 요청에는 요청 내용 때문에 거절되는 경우가 없다. 메시지 형식이 틀리면 COMMON_ERROR(INVALID_MESSAGE)가 요청자에게만 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = @ExampleObject(name = "INVALID_MESSAGE", summary = "INVALID_MESSAGE", value = DocExamples.COMMON_INVALID_MESSAGE)))
    @PostMapping("/requests/room-list-request")
    public void roomListRequest() {
    }

    @Operation(summary = "방만들기", description = "type: CREATE_ROOM. 대기실을 만든다. 요청 본문은 없다. 맵과 최대 인원은 이 요청에 없다. 만들 때는 기본 맵으로 정해지고, 클라가 이어서 보내는 ROOM_CONFIG_REQUEST로 맵과 최대 인원을 정한다. 만든 사람이 방장이 되고 자동으로 참가한다. 결과는 요청자에게 ROOM_ENTERED, 대기실 전원에게 ROOM_UPDATED 알림으로 간다.\n\n"
            + "오류: ALREADY_IN_ROOM (이미 어떤 방에 있음, 시작한 방 포함)")
    @ApiResponse(responseCode = "200", description = "**ROOM_ENTERED** — 방 입장과 같은 알림이 요청자에게 간다. 필드와 설명은 \"방입장\" 페이지를 본다.\n\n")
    @ApiResponse(responseCode = "400", description = "거절되면 **LOBBY_ERROR**가 요청자에게만 가고 서버 상태는 바뀌지 않는다. 가능한 오류 코드는 오른쪽 예시에서 고를 수 있다. 메시지 형식이 틀리면 COMMON_ERROR(INVALID_MESSAGE)가 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = {
                            @ExampleObject(name = "ALREADY_IN_ROOM", summary = "ALREADY_IN_ROOM", value = DocExamples.LOBBY_ALREADY_IN_ROOM),
                            @ExampleObject(name = "INVALID_MESSAGE", summary = "INVALID_MESSAGE", value = DocExamples.COMMON_INVALID_MESSAGE)}))
    @PostMapping("/requests/create-room")
    public void createRoom() {
    }

    @Operation(summary = "방입장", description = "type: ENTER_ROOM. 대기실에 입장한다. roomId는 대기실 목록(ROOM_LIST)에서 고른 방 번호이거나, 코드로 입장할 때 입력한 방 번호다. 결과는 요청자에게 ROOM_ENTERED, 대기실 전원에게 ROOM_UPDATED 알림으로 간다.\n\n"
            + "오류 (검증 순서): ROOM_NOT_FOUND (없는 방) → ROOM_ALREADY_STARTED (시작한 방) → ALREADY_IN_ROOM (이미 어떤 방에 있음) → ROOM_FULL (가득 참)")
    @ApiResponse(responseCode = "200", description = "**ROOM_ENTERED** — 대기실을 만들거나 대기실에 들어가는 데 성공했다는 알림으로 요청자에게만 간다. 클라는 이 알림으로 대기실 화면으로 넘어간다. payload는 없고, 대기실 상태는 ROOM_UPDATED로 온다.\n\n",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"),
                    examples = @ExampleObject(name = "ROOM_ENTERED", summary = "ROOM_ENTERED", value = DocExamples.ROOM_ENTERED)))
    @ApiResponse(responseCode = "400", description = "거절되면 **LOBBY_ERROR**가 요청자에게만 가고 서버 상태는 바뀌지 않는다. 가능한 오류 코드는 오른쪽 예시에서 고를 수 있다. 메시지 형식이 틀리면 COMMON_ERROR(INVALID_MESSAGE)가 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = {
                            @ExampleObject(name = "ROOM_NOT_FOUND", summary = "ROOM_NOT_FOUND", value = DocExamples.LOBBY_ROOM_NOT_FOUND),
                            @ExampleObject(name = "ROOM_ALREADY_STARTED", summary = "ROOM_ALREADY_STARTED", value = DocExamples.LOBBY_ROOM_ALREADY_STARTED),
                            @ExampleObject(name = "ALREADY_IN_ROOM", summary = "ALREADY_IN_ROOM", value = DocExamples.LOBBY_ALREADY_IN_ROOM),
                            @ExampleObject(name = "ROOM_FULL", summary = "ROOM_FULL", value = DocExamples.LOBBY_ROOM_FULL),
                            @ExampleObject(name = "INVALID_MESSAGE", summary = "INVALID_MESSAGE", value = DocExamples.COMMON_INVALID_MESSAGE)}))
    @PostMapping("/requests/enter-room")
    public void enterRoom(@RequestBody EnterRoomRequestPayload body) {
    }
}