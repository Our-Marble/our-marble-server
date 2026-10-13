package com.sparta.ourmarbleserver.lobby.controller;

import java.util.List;

import com.sparta.ourmarbleserver.global.protocol.ConnectedPayload;
import com.sparta.ourmarbleserver.lobby.dto.RoomListPayload;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sparta.ourmarbleserver.lobby.dto.GameStartedPayload;
import com.sparta.ourmarbleserver.lobby.dto.RoomStatus;
import com.sparta.ourmarbleserver.lobby.dto.RoomUpdatedPayload;
import com.sparta.ourmarbleserver.lobby.dto.RoomUpdatedPayload.PlayerSnapshot;
import com.sparta.ourmarbleserver.lobby.dto.RoomUpdatedPayload.RoomSnapshot;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Redoc 문서 전용 컨트롤러. 서버가 방 전원에게 보내는 로비 알림의 payload 모양과 예시 값을 문서에 보여 주려고 만들었다.
 * 필드 이름과 타입은 payload dto에서 자동으로 읽고, 예시 값은 @ExampleObject에 적은 값이다.
 * 실제 로비 로직과 무관하다.
 */
@Tag(name = "로비 알림", description = "서버가 WebSocket으로 방 전원에게 보내는 로비 알림")
@RestController
@RequestMapping("/docs/notifications")
public class LobbyMessageDocController {

    @Operation(summary = "접속완료", description = "WebSocket 연결 직후 서버가 본인에게만 보낸다. 클라는 playerId를 직접 정하지 않고 이 값을 로컬 플레이어 번호로 저장한다 [예시 JSON 열기](/docs/notifications/connected)")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConnectedPayload.class),
                    examples = @ExampleObject(value = "{\"playerId\": 7, \"nickname\": \"Alice\"}")))
    @GetMapping("/connected")
    public ConnectedPayload connected() {
        return new ConnectedPayload(7L, "Alice");
    }

    @Operation(summary = "방입장성공", description = "요청 CREATE_ROOM, ENTER_ROOM → 알림 ROOM_ENTERED. 방을 만들거나 방에 들어가는 데 성공했다는 알림으로 요청자에게만 간다. 클라는 이 알림으로 대기창으로 넘어간다. payload는 없고, 방 상태는 ROOM_UPDATED로 온다.")
    @ApiResponse(responseCode = "200", description = "payload 없음")
    @GetMapping("/room-entered")
    public void roomEntered() {
    }

    @Operation(summary = "방나가기성공", description = "요청 LEAVE_ROOM → 알림 ROOM_LEFT. 방에서 나가는 데 성공했다는 알림으로 요청자에게만 간다. payload는 없다.")
    @ApiResponse(responseCode = "200", description = "payload 없음")
    @GetMapping("/room-left")
    public void roomLeft() {
    }

    @Operation(summary = "방목록", description = "요청 REFRESH_ROOM_LIST → 알림 ROOM_LIST. 대기 중인 방 목록으로, 요청자에게만 간다. 방이 없으면 rooms는 빈 배열이다 [예시 JSON 열기](/docs/notifications/room-list)")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoomListPayload.class),
                    examples = @ExampleObject(value = "{\"rooms\": [{\"roomId\": \"r_3\", \"hostNickname\": \"철수\", \"mapId\": 1, \"playerCount\": 2}]}")))
    @GetMapping("/room-list")
    public RoomListPayload roomList() {
        return new RoomListPayload(List.of(new RoomListPayload.RoomSummary("r_3", "철수", 1, 2)));
    }

    @Operation(summary = "방상태변경", description = "요청 CREATE_ROOM, ENTER_ROOM, LEAVE_ROOM, SET_READY → 알림 ROOM_UPDATED. 방 상태가 바뀌었다 (입장, 퇴장, 준비, 방장 변경). players의 순서가 참가 순서이고 게임 시작 시 턴 순서가 된다. 방장은 항상 ready=true. status는 WAITING, PLAYING [예시 JSON 열기](/docs/notifications/room-updated)")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoomUpdatedPayload.class),
                    examples = @ExampleObject(value = "{\"room\": {\"roomId\": \"r_3\", \"hostId\": 1, \"mapId\": 1, \"status\": \"WAITING\", \"players\": [{\"playerId\": 1, \"nickname\": \"Alice\", \"ready\": true}, {\"playerId\": 2, \"nickname\": \"Bob\", \"ready\": false}]}}")))
    @GetMapping("/room-updated")
    public RoomUpdatedPayload roomUpdated() {
        return new RoomUpdatedPayload(new RoomSnapshot("r_3", 1L, 1, RoomStatus.WAITING,
                List.of(new PlayerSnapshot(1L, "Alice", true), new PlayerSnapshot(2L, "Bob", false))));
    }

    @Operation(summary = "게임시작", description = "요청 START_GAME → 알림 GAME_STARTED. 게임이 시작됐다. 클라는 이 알림으로 게임 화면으로 넘어간다 [예시 JSON 열기](/docs/notifications/game-started)")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = GameStartedPayload.class),
                    examples = @ExampleObject(value = "{\"roomId\": \"r_3\"}")))
    @GetMapping("/game-started")
    public GameStartedPayload gameStarted() {
        return new GameStartedPayload("r_3");
    }
}