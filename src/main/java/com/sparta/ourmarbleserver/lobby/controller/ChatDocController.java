package com.sparta.ourmarbleserver.lobby.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sparta.ourmarbleserver.config.DocExamples;
import com.sparta.ourmarbleserver.global.protocol.ErrorPayload;
import com.sparta.ourmarbleserver.lobby.dto.ChatBroadcastPayload;
import com.sparta.ourmarbleserver.lobby.dto.ChatSendRequestPayload;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Redoc 문서 전용 컨트롤러. 대기실 채팅(CHAT_SEND → CHAT_BROADCAST) 문서다.
 * 한 페이지에 요청(오른쪽 위 예시)과 그 결과 알림·오류(응답 예시)를 함께 보여 준다. 요청 본문과 알림 필드는 각 dto에서 자동으로 읽고,
 * 예시 JSON은 DocExamples에 있다. 실제 로직과 무관하고, 호출해도 아무 일도 일어나지 않는다.
 */
@Tag(name = "채팅", description = "대기실 채팅 메시지. 서버는 채팅을 저장하지 않고, 글자 수 제한과 도배 방지도 하지 않는다.")
@RestController
@RequestMapping("/docs")
public class ChatDocController {

    @Operation(summary = "채팅보내기", description = "type: CHAT_SEND. 내가 들어 있는 대기실에 채팅을 보낸다. 방 번호는 서버가 보낸 사람의 위치로 안다. "
            + "결과는 보낸 본인을 포함한 대기실 전원에게 CHAT_BROADCAST 알림으로 간다. content가 빈 문자열이거나 공백뿐이면 아무 일도 일어나지 않는다(응답도 오류도 없다). "
            + "글자 수 제한과 도배 방지는 서버에서 하지 않는다.\n\n"
            + "오류 (검증 순서): COMMON_ERROR(INVALID_MESSAGE) (content가 없음) → NOT_IN_ROOM (어느 방에도 없음) → INVALID_STATE (게임이 시작된 방)")
    @ApiResponse(responseCode = "200", description = "**CHAT_BROADCAST** — 방에서 채팅이 왔다. 보낸 본인을 포함한 대기실 전원에게 간다. playerId는 보낸 사람의 번호이고 content는 채팅 내용이다. "
            + "시각 필드는 없고 클라가 받은 시각을 쓴다. 나중에 입장한 사람에게 지난 채팅은 보내지 않는다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatBroadcastPayload.class),
                    examples = @ExampleObject(name = "CHAT_BROADCAST", summary = "CHAT_BROADCAST", value = DocExamples.CHAT_BROADCAST)))
    @ApiResponse(responseCode = "400", description = "거절되면 **LOBBY_ERROR**가 요청자에게만 가고 서버 상태는 바뀌지 않는다. 가능한 오류 코드는 오른쪽 예시에서 고를 수 있다. content가 없으면 COMMON_ERROR(INVALID_MESSAGE)가 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = {
                            @ExampleObject(name = "NOT_IN_ROOM", summary = "NOT_IN_ROOM", value = DocExamples.LOBBY_NOT_IN_ROOM),
                            @ExampleObject(name = "INVALID_STATE", summary = "INVALID_STATE", value = DocExamples.LOBBY_INVALID_STATE),
                            @ExampleObject(name = "INVALID_MESSAGE", summary = "INVALID_MESSAGE", value = DocExamples.COMMON_INVALID_MESSAGE)}))
    @PostMapping("/requests/chat-send")
    public void chatSend(@RequestBody ChatSendRequestPayload body) {
    }
}