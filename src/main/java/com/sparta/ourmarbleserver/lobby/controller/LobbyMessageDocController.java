package com.sparta.ourmarbleserver.lobby.controller;

import java.util.List;

import com.sparta.ourmarbleserver.game.state.GameConfig;
import com.sparta.ourmarbleserver.global.protocol.ConnectedPayload;
import com.sparta.ourmarbleserver.lobby.dto.PlayerRecordPayload;
import com.sparta.ourmarbleserver.lobby.dto.RoomListPayload;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sparta.ourmarbleserver.lobby.dto.GameStartedPayload;
import com.sparta.ourmarbleserver.lobby.dto.RoomStatus;
import com.sparta.ourmarbleserver.lobby.dto.RoomUpdatedPayload;
import com.sparta.ourmarbleserver.lobby.dto.RoomUpdatedPayload.PlayerSnapshot;
import com.sparta.ourmarbleserver.lobby.dto.RoomUpdatedPayload.RoomSnapshot;

import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.transport.MessageCodec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Redoc 문서 전용 컨트롤러. 서버가 클라이언트에게 보내는 로비 알림의 payload 모양과 예시 값을 문서에 보여 주려고 만들었다.
 * 필드 이름과 타입은 payload dto에서 자동으로 읽고, 예시 값은 @ExampleObject에 적은 값이다.
 * 실제 로비 로직과 무관하다.
 */
@Tag(name = "로비 알림", description = "서버가 WebSocket으로 클라이언트에게 보내는 로비 알림")
@RestController
@RequestMapping("/docs/notifications")
public class LobbyMessageDocController {

    private final MessageCodec codec;

    public LobbyMessageDocController(MessageCodec codec) {
        this.codec = codec;
    }

    @Operation(summary = "접속완료", description = "WebSocket 연결 직후 서버가 본인에게만 보낸다. 클라는 playerId를 직접 정하지 않고 이 값을 로컬 플레이어 번호로 저장한다. 이어서 게임 중인 방이 있으면 GAME_STARTED가, 없으면 대기실 목록(ROOM_LIST)과 내 전적(PLAYER_RECORD)이 본인에게 간다. 접속할 때 이미 대기실에 들어가 있는 사람(게임이 끝나 대기실로 돌아온 방에 남아 있던 경우)은 그 대기실에서 나가게 되고, 남은 사람에게 ROOM_UPDATED가 간다 [예시 JSON 열기](/docs/notifications/connected)")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConnectedPayload.class),
                    examples = @ExampleObject(value = "{\"type\": \"CONNECTED\", \"playerId\": 7, \"nickname\": \"Alice\"}")))
    @GetMapping(value = "/connected", produces = "application/json")
    public String connected() {
        return codec.encode(MessageType.CONNECTED, new ConnectedPayload(7L, "Alice"));
    }

    @Operation(summary = "방입장성공", description = "요청 CREATE_ROOM, ENTER_ROOM → 알림 ROOM_ENTERED. 대기실을 만들거나 대기실에 들어가는 데 성공했다는 알림으로 요청자에게만 간다. 클라는 이 알림으로 대기실 화면으로 넘어간다. payload는 없고, 대기실 상태는 ROOM_UPDATED로 온다.")
    @ApiResponse(responseCode = "200", description = "payload 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"),
                    examples = @ExampleObject(value = "{\"type\": \"ROOM_ENTERED\"}")))
    @GetMapping(value = "/room-entered", produces = "application/json")
    public String roomEntered() {
        return codec.encode(MessageType.ROOM_ENTERED, null);
    }

    @Operation(summary = "방나가기성공", description = "요청 LEAVE_ROOM → 알림 ROOM_LEFT. 대기실에서 나가는 데 성공했다는 알림으로 요청자에게만 간다. 이어서 최신 대기실 목록(ROOM_LIST)과 내 전적(PLAYER_RECORD)이 간다. payload는 없다.")
    @ApiResponse(responseCode = "200", description = "payload 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"),
                    examples = @ExampleObject(value = "{\"type\": \"ROOM_LEFT\"}")))
    @GetMapping(value = "/room-left", produces = "application/json")
    public String roomLeft() {
        return codec.encode(MessageType.ROOM_LEFT, null);
    }

    @Operation(summary = "방목록", description = "요청 ROOM_LIST_REQUEST → 알림 ROOM_LIST. 대기실 목록으로, 요청자에게만 간다. 대기실이 없으면 rooms는 빈 배열이다. playerCount는 지금 참가한 인원수(봇 포함), maxPlayers는 최대 인원이다 [예시 JSON 열기](/docs/notifications/room-list)\n\n"
            + "**ROOM_LIST가 가는 때**\n\n"
            + "- 접속 직후: CONNECTED 다음에 간다. 게임 중인 방이 있으면 ROOM_LIST 대신 GAME_STARTED가 간다.\n"
            + "- 대기실 목록 요청(ROOM_LIST_REQUEST)을 보냈을 때\n"
            + "- 대기실에서 나간 직후(LEAVE_ROOM): ROOM_LEFT와 함께 간다.\n\n"
            + "세 경우 모두 ROOM_LIST 바로 뒤에 내 전적(PLAYER_RECORD)이 같이 간다. 목록이 바뀌어도 서버가 자동으로 보내지 않으니, 최신 목록은 ROOM_LIST_REQUEST로 받는다.")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoomListPayload.class),
                    examples = @ExampleObject(value = "{\"type\": \"ROOM_LIST\", \"rooms\": [{\"roomId\": \"r_3\", \"hostNickname\": \"철수\", \"mapId\": 1, \"playerCount\": 2, \"maxPlayers\": 4}]}")))
    @GetMapping(value = "/room-list", produces = "application/json")
    public String roomList() {
        return codec.encode(MessageType.ROOM_LIST, new RoomListPayload(List.of(new RoomListPayload.RoomSummary("r_3", "철수", 1, 2, 4))));
    }

    @Operation(summary = "내전적", description = "ROOM_LIST를 보낼 때마다 바로 뒤에 본인에게만 같이 가는 내 전적이다. 접속 직후, 대기실 목록 요청(ROOM_LIST_REQUEST), 대기실 나가기 직후에 가고, 게임 중인 방이 있어 접속 직후 GAME_STARTED가 가는 경우에는 가지 않는다. 전적을 조회하지 못하면 ROOM_LIST만 가고 이 알림은 가지 않는다. gamesPlayed는 플레이한 판 수, gamesWon은 이긴 판 수다 [예시 JSON 열기](/docs/notifications/player-record)")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlayerRecordPayload.class),
                    examples = @ExampleObject(value = "{\"type\": \"PLAYER_RECORD\", \"nickname\": \"Alice\", \"gamesPlayed\": 10, \"gamesWon\": 4}")))
    @GetMapping(value = "/player-record", produces = "application/json")
    public String playerRecord() {
        return codec.encode(MessageType.PLAYER_RECORD, new PlayerRecordPayload("Alice", 10, 4));
    }

    @Operation(summary = "방상태변경", description = "요청 CREATE_ROOM, ENTER_ROOM, LEAVE_ROOM, SET_READY, ROOM_CONFIG_REQUEST → 알림 ROOM_UPDATED. 대기실 상태가 바뀌었다 (입장, 퇴장, 준비, 방장 변경, 설정 변경). 대기실 전원에게 간다. 연결이 끊겨 대기실에서 퇴장 처리될 때도 남은 사람에게 간다. 게임이 끝나 방이 대기실로 돌아올 때도 방 전원에게 간다. 이때 방장을 뺀 사람의 준비는 풀려 있고, 게임 중에 연결이 끊긴 사람은 이미 빠져 있다. 게임 중이던 클라는 이 알림으로 대기실 화면으로 돌아온다. players의 순서가 참가 순서이고 게임 시작 시 턴 순서가 된다. 방장은 항상 ready=true다. 봇은 bot=true이고 playerId가 음수이며 항상 ready=true다. maxPlayers는 최대 인원(2~4), config는 방장이 정한 게임 설정이다. 방장이 설정을 바꾸면(ROOM_CONFIG_REQUEST) 대기실 전원에게 새 값이 담긴 이 알림이 간다. status는 WAITING, PLAYING [예시 JSON 열기](/docs/notifications/room-updated)")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoomUpdatedPayload.class),
                    examples = @ExampleObject(value = "{\"type\": \"ROOM_UPDATED\", \"room\": {\"roomId\": \"r_3\", \"hostId\": 1, \"mapId\": 1, \"status\": \"WAITING\", \"config\": {\"startMoney\": 500000, \"salaryAmount\": 100000, \"taxAmount\": 100000, \"maxRound\": 30, \"islandTurns\": 3, \"mapId\": 1, \"limitTimeSeconds\": 20}, \"players\": [{\"playerId\": 1, \"nickname\": \"Alice\", \"ready\": true, \"bot\": false}, {\"playerId\": 2, \"nickname\": \"Bob\", \"ready\": false, \"bot\": false}, {\"playerId\": -1, \"nickname\": \"봇1\", \"ready\": true, \"bot\": true}], \"maxPlayers\": 4}}")))
    @GetMapping(value = "/room-updated", produces = "application/json")
    public String roomUpdated() {
        return codec.encode(MessageType.ROOM_UPDATED, new RoomUpdatedPayload(new RoomSnapshot("r_3", 1L, 1, RoomStatus.WAITING,
                new GameConfig(500000L, 100000L, 100000L, 30, 3, 1, 20),
                List.of(new PlayerSnapshot(1L, "Alice", true, false),
                        new PlayerSnapshot(2L, "Bob", false, false),
                        new PlayerSnapshot(-1L, "봇1", true, true)),
                4)));
    }

    @Operation(summary = "게임시작", description = "요청 START_GAME → 알림 GAME_STARTED. 게임이 시작됐다. 방 전원에게 간다. 클라는 이 알림으로 게임 화면으로 넘어간다. 접속 직후 게임 중인 방이 있을 때도 접속한 사람에게 간다 [예시 JSON 열기](/docs/notifications/game-started)")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = GameStartedPayload.class),
                    examples = @ExampleObject(value = "{\"type\": \"GAME_STARTED\", \"roomId\": \"r_3\"}")))
    @GetMapping(value = "/game-started", produces = "application/json")
    public String gameStarted() {
        return codec.encode(MessageType.GAME_STARTED, new GameStartedPayload("r_3"));
    }
}