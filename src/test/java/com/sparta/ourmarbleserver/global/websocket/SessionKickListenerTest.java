package com.sparta.ourmarbleserver.global.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SessionKickListenerTest {

    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);
    private final SessionKickListener listener = new SessionKickListener(sessionRegistry);

    private static Message messageOf(String body) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        return message;
    }

    @Test
    void 받은_요청대로_그_연결을_끊는다() {
        listener.onMessage(messageOf("7:abc-123"), null);

        verify(sessionRegistry).closeDuplicate(7L, "abc-123");
    }

    @Test
    void 형식이_틀린_메시지는_무시한다() {
        listener.onMessage(messageOf("이상한값"), null);

        verify(sessionRegistry, never()).closeDuplicate(anyLong(), anyString());
    }
}
