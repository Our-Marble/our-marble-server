package com.sparta.ourmarbleserver.global.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SessionRegistryTest {

    private InMemoryPresenceRepository presence;
    private SessionRegistry registry;

    @BeforeEach
    void setUp() {
        presence = new InMemoryPresenceRepository();
        registry = new SessionRegistry(presence, new ServerInstance("A"));   // 이 서버는 A
    }

    /** 열려 있는 가짜 세션 */
    private WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    // ===== 이 서버 연결 =====

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
        assertThat(registry.isConnected(1L)).isTrue();
        assertThat(presence.tokenOf(1L)).isEqualTo("A:b");
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

    @Test
    void 접속_중인_플레이어_목록을_돌려준다() {
        registry.register(1L, openSession("s1"));
        registry.register(2L, openSession("s2"));

        assertThat(registry.connectedPlayerIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    // ===== 서버 사이 접속 기록 (presence) =====

    @Test
    void 접속하면_서버id와_세션id로_기록한다() {
        registry.register(1L, openSession("a"));

        assertThat(presence.tokenOf(1L)).isEqualTo("A:a");
    }

    @Test
    void 다른_서버에_접속_중이면_접속_중으로_본다() {
        presence.markOnline(1L, "B:x");   // 서버 B에 붙어 있음

        assertThat(registry.isConnected(1L)).isTrue();
        assertThat(registry.connectedPlayerIds()).doesNotContain(1L);   // 이 서버 연결은 아니다
    }

    @Test
    void 다른_서버로_재접속한_뒤_옛_연결이_끊겨도_새_기록은_지우지_않는다() {
        WebSocketSession a = openSession("a");
        registry.register(1L, a);
        presence.markOnline(1L, "B:x");   // 서버 B로 재접속

        registry.remove(1L, a);           // 서버 A의 옛 연결 종료

        assertThat(registry.isConnected(1L)).isTrue();
        assertThat(presence.tokenOf(1L)).isEqualTo("B:x");
    }

    @Test
    void 이_서버의_연결들만_연장한다() {
        PresenceRepository mockPresence = mock(PresenceRepository.class);
        SessionRegistry registry = new SessionRegistry(mockPresence, new ServerInstance("A"));
        registry.register(1L, openSession("a"));
        registry.register(2L, openSession("b"));

        registry.refreshPresence();

        verify(mockPresence).refresh(Map.of(1L, "A:a", 2L, "A:b"));
    }

    @Test
    void 접속_기록_삭제가_실패해도_종료_처리는_예외가_나지_않는다() {
        PresenceRepository mockPresence = mock(PresenceRepository.class);
        doThrow(new RuntimeException("Redis 끊김")).when(mockPresence).markOffline(anyLong(), anyString());
        SessionRegistry registry = new SessionRegistry(mockPresence, new ServerInstance("A"));
        WebSocketSession a = openSession("a");
        registry.register(1L, a);

        assertThatCode(() -> registry.remove(1L, a)).doesNotThrowAnyException();
    }

    @Test
    void 서버가_내려갈_때_이_서버의_연결을_모두_닫는다() throws Exception {
        WebSocketSession a = openSession("a");
        WebSocketSession b = openSession("b");
        registry.register(1L, a);
        registry.register(2L, b);

        registry.closeAll();

        verify(a).close(CloseStatus.SERVICE_RESTARTED);
        verify(b).close(CloseStatus.SERVICE_RESTARTED);
    }
}