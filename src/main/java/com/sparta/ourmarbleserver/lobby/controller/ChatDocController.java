package com.sparta.ourmarbleserver.lobby.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sparta.ourmarbleserver.config.DocExamples;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Redoc 문서 전용 컨트롤러. 대기실 채팅 문서의 자리 표시(흔적)다.
 * 채팅 코드(MessageType의 CHAT_SEND, CHAT_BROADCAST, payload dto)가 dev에 들어오면, 이 컨트롤러를 지우고 다른 페이지처럼
 * dto와 오류 코드 기준으로 다시 만든다. 지금은 알려진 내용(필드 이름)만 직접 적었다.
 */
@Tag(name = "채팅", description = "대기실 채팅 메시지. (작성 예정: 채팅 코드가 들어오면 필드와 오류 코드를 코드 기준으로 확정한다)")
@RestController
@RequestMapping("/docs")
public class ChatDocController {

    @Operation(summary = "채팅보내기", description = "type: CHAT_SEND. **작성 예정.** 대기실에서 채팅을 보낸다. 본문은 content 하나이고, "
            + "결과는 CHAT_BROADCAST 알림으로 간다. 채팅은 서버 메모리에 저장하는 방식으로 알려져 있어 서버를 재시작하면 사라진다. "
            + "보내는 사람이 받는지, 글자 수·연속 전송 제한, 오류 코드, 지난 채팅을 받는 방식은 코드가 들어오면 확정한다.")
    @RequestBody(content = @Content(mediaType = "application/json", schema = @Schema(type = "object"),
            examples = @ExampleObject(name = "CHAT_SEND", summary = "CHAT_SEND", value = DocExamples.CHAT_SEND)))
    @ApiResponse(responseCode = "200", description = "**CHAT_BROADCAST** — 대기실 전원에게 간다. 필드는 playerId(보낸 사람)와 content(내용)다. (코드 확인 전)",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"),
                    examples = @ExampleObject(name = "CHAT_BROADCAST", summary = "CHAT_BROADCAST", value = DocExamples.CHAT_BROADCAST)))
    @PostMapping("/requests/chat-send")
    public void chatSend() {
    }
}