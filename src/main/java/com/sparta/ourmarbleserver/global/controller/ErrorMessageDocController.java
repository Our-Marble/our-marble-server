package com.sparta.ourmarbleserver.global.controller;

import com.sparta.ourmarbleserver.global.protocol.GameErrorCode;
import com.sparta.ourmarbleserver.global.protocol.ErrorPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redoc 문서 전용 컨트롤러. 게임·로비 공통으로 요청이 거절됐을 때 가는 ERROR 알림의 모양과 오류 코드 목록을 문서에 보여 주려고 만들었다.
 * 오류 코드 설명은 ErrorCode에 맞춰 직접 적은 값이라, ErrorCode가 바뀌면 같이 고쳐야 한다. 실제 로직과 무관하다.
 */
@Tag(name = "오류 알림", description = "요청이 거절됐을 때 서버가 WebSocket으로 요청자에게만 보내는 알림")
@RestController
@RequestMapping("/docs/notifications")
public class ErrorMessageDocController {

    @Operation(summary = "오류", description = """
            어떤 요청이든 거절되면 서버가 요청한 사람에게만 보낸다. 오류가 나도 서버 상태는 바뀌지 않는다. 클라는 `code`로 분기하고, `message`는 사람이 읽는 기본 문구다. [예시 JSON 열기](/docs/notifications/error)
 
            **오류 코드**
 
            | 구분 | code | message | 상황 |
            | --- | --- | --- | --- |
            | 공통 | `NOT_YOUR_TURN` | 내 차례가 아닙니다. | 내 차례가 아닌데 요청 |
            | 공통 | `INVALID_STATE` | 지금은 할 수 없는 요청입니다. | 진행 중인 게임이 없거나, 현재 단계(phase)에서 받을 수 없는 요청 |
            | 공통 | `NOT_ENOUGH_MONEY` | 현금이 부족합니다. | 구매·건설·인수에 필요한 현금이 모자람 |
            | 공통 | `INVALID_PROPERTY` | 올바르지 않은 땅입니다. | 없는 땅, 내 말이 서 있지 않은 땅, 올바르지 않은 목적지 칸 |
            | 공통 | `INVALID_MESSAGE` | 메시지 형식이 올바르지 않습니다. | JSON이 아니거나 type이 없거나 모르는 값, 필수 필드 누락 |
            | 게임 | `ALREADY_OWNED` | 이미 주인이 있는 땅입니다. | 주인 있는 땅 구매 |
            | 게임 | `NOT_OWNER` | 내 땅이 아닙니다. | 내 땅이 아닌데 건설·매각 |
            | 게임 | `CANNOT_BUILD` | 건설할 수 없는 땅입니다. | 건설 불가 칸 (독도 등) |
            | 게임 | `MAX_LEVEL` | 더 이상 건설할 수 없습니다. | 이미 호텔 |
            | 게임 | `CANNOT_ACQUIRE` | 인수할 수 없는 땅입니다. | 주인 없는 땅이나 내 땅 인수 |
            | 게임 | `NOT_ENOUGH_SELL` | 선택한 땅을 팔아도 통행료가 부족합니다. | 고른 땅을 팔아도 통행료 부족 |
            | 게임 | `PLAYER_BANKRUPT` | 파산한 플레이어입니다. | 파산한 플레이어의 요청 |
            | 게임 | `INVALID_PROPERTY_LIST` | 매각할 땅 목록이 올바르지 않습니다. | 매각 목록이 비었거나 중복 |
            | 로비 | `ROOM_NOT_FOUND` | 방을 찾을 수 없습니다. | 없는 방에 입장 |
            | 로비 | `ROOM_FULL` | 방이 가득 찼습니다. | 가득 찬 방에 입장 |
            | 로비 | `ALREADY_IN_ROOM` | 이미 방에 참가 중입니다. | 이미 방에 있는데 방만들기·방입장 |
            | 로비 | `NOT_IN_ROOM` | 방에 참가하지 않았습니다. | 방에 없는데 방나가기·준비·게임시작 |
            | 로비 | `NOT_HOST` | 방장만 할 수 있습니다. | 방장이 아닌데 게임시작 |
            | 로비 | `ROOM_ALREADY_STARTED` | 이미 시작한 방입니다. | 시작한 방에 입장·나가기·준비·게임시작 |
            | 로비 | `NOT_ENOUGH_PLAYERS` | 최소 2명이 필요합니다. | 2명 미만으로 게임시작 |
            | 로비 | `NOT_ALL_READY` | 모두 준비해야 시작할 수 있습니다. | 준비 안 한 사람이 있는데 게임시작 |
            """)
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class), examples = @ExampleObject(value = "{\"code\": \"NOT_YOUR_TURN\", \"message\": \"내 차례가 아닙니다.\"}")))
    @GetMapping("/error")
    public ErrorPayload error() {
        return ErrorPayload.of(GameErrorCode.NOT_YOUR_TURN);
    }
}
