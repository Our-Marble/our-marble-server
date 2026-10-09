package com.sparta.ourmarbleserver.global.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionRegistryTest {

    private SessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SessionRegistry();
    }

    /** 열려 있는 가짜 세션 */
    private WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    void 등록한_플레이어에게_보낸다() throws Exception {
        WebSocketSession a = openSession("a");
        registry.register(1L, a);

        registry.send(1L, "hi");

        verify(a).sendMessage(new TextMessage("hi"));
    }

    @Test
    void 같은_플레이어가_다시_접속하면_옛_연결을_DUPLICATE_LOGIN으로_끊는다() throws Exception {
        WebSocketSession a = openSession("a");
        WebSocketSession b = openSession("b");
        registry.register(1L, a);

        registry.register(1L, b);

        verify(a).close(SessionCloseCodes.DUPLICATE_LOGIN);
    }

    @Test
    void 옛_연결의_종료가_늦게_와도_새_연결은_지워지지_않는다() throws Exception {
        WebSocketSession a = openSession("a");
        WebSocketSession b = openSession("b");
        registry.register(1L, a);
        registry.register(1L, b);

        registry.remove(1L, a);   // 옛 연결 a의 종료 알림이 늦게 도착
        registry.send(1L, "hi");

        verify(b).sendMessage(new TextMessage("hi"));
        verify(a, never()).sendMessage(any());
    }

    @Test
    void 자기_연결이면_지운다() {
        WebSocketSession a = openSession("a");
        registry.register(1L, a);

        registry.remove(1L, a);

        assertThat(registry.isConnected(1L)).isFalse();
    }

    @Test
    void 접속하지_않은_플레이어에게_보내도_예외가_나지_않는다() {
        assertThatCode(() -> registry.send(99L, "hi")).doesNotThrowAnyException();
    }
}