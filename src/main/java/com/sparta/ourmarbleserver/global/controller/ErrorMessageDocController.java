package com.sparta.ourmarbleserver.global.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sparta.ourmarbleserver.global.protocol.CommonErrorCode;
import com.sparta.ourmarbleserver.global.protocol.ErrorPayload;
import com.sparta.ourmarbleserver.global.protocol.GameErrorCode;
import com.sparta.ourmarbleserver.global.protocol.LobbyErrorCode;

import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.transport.MessageCodec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Redoc 문서 전용 컨트롤러. 요청이 거절됐을 때 가는 오류 알림 3종(COMMON_ERROR, LOBBY_ERROR, GAME_ERROR)의 모양과
 * 오류 코드 목록을 문서에 보여 주려고 만들었다. type은 오류 코드가 속한 enum(CommonErrorCode, LobbyErrorCode, GameErrorCode)이 정한다.
 * 오류 코드 표는 ErrorCodeDocConfig가 각 enum에서 읽어 설명 끝에 붙인다. 실제 로직과 무관하다.
 */
@Tag(name = "오류 알림", description = "요청이 거절됐을 때 서버가 WebSocket으로 요청자에게만 보내는 알림")
@RestController
@RequestMapping("/docs/notifications")
public class ErrorMessageDocController {

    private final MessageCodec codec;

    public ErrorMessageDocController(MessageCodec codec) {
        this.codec = codec;
    }

    @Operation(summary = "공통오류", description = """
            type: COMMON_ERROR. 로비 요청인지 게임 요청인지 구분할 수 없는 요청이 거절되면 서버가 요청한 사람에게만 보낸다. 오류가 나도 서버 상태는 바뀌지 않는다. 클라는 `type`으로 화면을 고르고 `code`로 세부 분기하며, `message`는 사람이 읽는 기본 문구다. [예시 JSON 열기](/docs/notifications/common-error)
            """)
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = @ExampleObject(value = "{\"type\": \"COMMON_ERROR\", \"code\": \"INVALID_MESSAGE\", \"message\": \"메시지 형식이 올바르지 않습니다.\"}")))
    @GetMapping(value = "/common-error", produces = "application/json")
    public String commonError() {
        return codec.encode(MessageType.COMMON_ERROR, ErrorPayload.of(CommonErrorCode.INVALID_MESSAGE));
    }

    @Operation(summary = "로비오류", description = """
            type: LOBBY_ERROR. 로비·대기방 요청(방만들기, 방입장, 방나가기, 준비, 게임시작)이 거절되면 서버가 요청한 사람에게만 보낸다. 오류가 나도 서버 상태는 바뀌지 않는다. 클라는 `type`으로 화면을 고르고 `code`로 세부 분기하며, `message`는 사람이 읽는 기본 문구다. [예시 JSON 열기](/docs/notifications/lobby-error)
            """)
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = @ExampleObject(value = "{\"type\": \"LOBBY_ERROR\", \"code\": \"ROOM_FULL\", \"message\": \"방이 가득 찼습니다.\"}")))
    @GetMapping(value = "/lobby-error", produces = "application/json")
    public String lobbyError() {
        return codec.encode(MessageType.LOBBY_ERROR, ErrorPayload.of(LobbyErrorCode.ROOM_FULL));
    }

    @Operation(summary = "게임오류", description = """
            type: GAME_ERROR. 게임 요청(주사위, 구매, 건설 등)이 거절되면 서버가 요청한 사람에게만 보낸다. 오류가 나도 서버 상태는 바뀌지 않는다. 클라는 `type`으로 화면을 고르고 `code`로 세부 분기하며, `message`는 사람이 읽는 기본 문구다. [예시 JSON 열기](/docs/notifications/game-error)
            """)
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = @ExampleObject(value = "{\"type\": \"GAME_ERROR\", \"code\": \"NOT_YOUR_TURN\", \"message\": \"내 차례가 아닙니다.\"}")))
    @GetMapping(value = "/game-error", produces = "application/json")
    public String gameError() {
        return codec.encode(MessageType.GAME_ERROR, ErrorPayload.of(GameErrorCode.NOT_YOUR_TURN));
    }
}