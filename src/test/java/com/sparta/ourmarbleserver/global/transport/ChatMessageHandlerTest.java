package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.CommonErrorCode;
import com.sparta.ourmarbleserver.global.protocol.LobbyErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.websocket.WebSocketMessageSender;
import com.sparta.ourmarbleserver.lobby.dto.ChatBroadcastPayload;
import com.sparta.ourmarbleserver.lobby.service.ChatService;
import com.sparta.ourmarbleserver.lobby.service.LobbyService;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageHandlerTest {

    private final ChatService chatService = mock(ChatService.class);
    private final WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageCodec codec = new MessageCodec(objectMapper);
    private final ChatMessageHandler handler = new ChatMessageHandler(chatService, sender, objectMapper);

    private JsonNode body(String json) {
        return objectMapper.readTree(json);
    }

    @Test
    void CHAT_SEND만_맡는다() {
        assertThat(handler.types()).containsExactly(MessageType.CHAT_SEND);
    }

    @Test
    void 보낼_방이_있으면_방_전원에게_CHAT_BROADCAST를_보낸다() {
        when(chatService.roomIdToSend(1L, "하이")).thenReturn(Optional.of("r_1"));

        handler.handle(MessageType.CHAT_SEND, 1L, body("{\"content\":\"하이\"}"));

        verify(sender).sendToRoom("r_1", MessageType.CHAT_BROADCAST, new ChatBroadcastPayload(1L, "하이"));
    }

    @Test
    void 빈_문자열은_보낼_방이_없다고_돌아와서_아무에게도_보내지_않는다() {
        when(chatService.roomIdToSend(1L, "")).thenReturn(Optional.empty());

        handler.handle(MessageType.CHAT_SEND, 1L, body("{\"content\":\"\"}"));

        verify(sender, never()).sendToRoom(anyString(), any(), any());
    }

    @Test
    void 방에_없거나_게임_중이면_ChatService의_오류가_그대로_올라가고_전송하지_않는다() {
        when(chatService.roomIdToSend(1L, "하이")).thenThrow(new GameException(LobbyErrorCode.NOT_IN_ROOM));

        assertThatThrownBy(() -> handler.handle(MessageType.CHAT_SEND, 1L, body("{\"content\":\"하이\"}")))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(LobbyErrorCode.NOT_IN_ROOM));
        verify(sender, never()).sendToRoom(anyString(), any(), any());
    }

    @Test
    void content가_없거나_null이면_INVALID_MESSAGE() {
        for (String json : List.of("{}", "{\"content\":null}")) {
            assertThatThrownBy(() -> handler.handle(MessageType.CHAT_SEND, 1L, body(json)))
                    .isInstanceOfSatisfying(GameException.class,
                            e -> assertThat(e.code()).isEqualTo(CommonErrorCode.INVALID_MESSAGE));
        }
        verify(chatService, never()).roomIdToSend(anyLong(), any());
    }

    @Test
    void content가_문자열로_읽을_수_없는_모양이면_본문_해석_실패로_올라간다() {
        // MainWebSocketHandler가 이 예외를 INVALID_MESSAGE로 바꿔 보낸다 (다른 요청과 같은 방식)
        assertThatThrownBy(() -> handler.handle(MessageType.CHAT_SEND, 1L, body("{\"content\":{\"a\":1}}")))
                .isInstanceOf(JacksonException.class);
        verify(chatService, never()).roomIdToSend(anyLong(), any());
    }

    @Test
    void 깨진_JSON은_해석_단계에서_INVALID_MESSAGE() {
        assertThatThrownBy(() -> codec.decode("{\"type\":\"CHAT_SEND\","))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(CommonErrorCode.INVALID_MESSAGE));
    }

    @Test
    void 라우터가_CHAT_SEND를_이_핸들러로_보낸다_다른_핸들러와_type이_겹치지_않는다() {
        LobbyMessageHandler lobbyHandler = new LobbyMessageHandler(
                mock(LobbyService.class), mock(WebSocketMessageSender.class), mock(LobbyScreenSender.class), objectMapper);
        MessageRouter router = new MessageRouter(List.of(lobbyHandler, handler));
        when(chatService.roomIdToSend(1L, "하이")).thenReturn(Optional.of("r_1"));

        router.route(1L, codec.decode("{\"type\":\"CHAT_SEND\",\"content\":\"하이\"}"));

        verify(sender).sendToRoom("r_1", MessageType.CHAT_BROADCAST, new ChatBroadcastPayload(1L, "하이"));
    }
}
