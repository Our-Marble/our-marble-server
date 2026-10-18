package com.sparta.ourmarbleserver.global.websocket;

import com.sparta.ourmarbleserver.game.event.GameEndedEvent;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.lobby.dto.RoomUpdatedPayload;
import com.sparta.ourmarbleserver.lobby.service.LobbyService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 게임 종료 후 연결 끊긴 멤버 정리 테스트. 봇은 연결이 없는 게 정상이라 정리 대상이 아니다. */
class GameEndedConnectionCleanerTest {

    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);
    private final LobbyService lobbyService = mock(LobbyService.class);
    private final WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
    private final GameEndedConnectionCleaner cleaner = new GameEndedConnectionCleaner(sessionRegistry, lobbyService, sender);

    private final RoomUpdatedPayload updated = new RoomUpdatedPayload(null);

    @Test
    void 끊긴_사람만_내보내고_봇은_남긴다() {
        when(lobbyService.memberIdsOf("r_1")).thenReturn(List.of(1L, 2L, -1L));   // 사람 1(접속), 사람 2(끊김), 봇 -1
        when(sessionRegistry.isConnected(1L)).thenReturn(true);
        when(sessionRegistry.isConnected(2L)).thenReturn(false);
        when(lobbyService.removeMembers("r_1", List.of(2L))).thenReturn(true);
        when(lobbyService.roomUpdated("r_1")).thenReturn(updated);

        cleaner.onGameEnded(new GameEndedEvent("r_1"));

        verify(lobbyService).removeMembers("r_1", List.of(2L));                    // 봇 -1은 포함되지 않음
        verify(sessionRegistry, never()).isConnected(-1L);                         // 봇은 접속 여부를 보지도 않음
        verify(sender).sendToRoom("r_1", MessageType.ROOM_UPDATED, updated);
    }

    @Test
    void 사람이_모두_접속해_있으면_아무도_내보내지_않고_대기방_정보만_알린다() {
        when(lobbyService.memberIdsOf("r_1")).thenReturn(List.of(1L, -1L, -2L));
        when(sessionRegistry.isConnected(1L)).thenReturn(true);
        when(lobbyService.roomUpdated("r_1")).thenReturn(updated);

        cleaner.onGameEnded(new GameEndedEvent("r_1"));

        verify(lobbyService, never()).removeMembers(anyString(), anyCollection());
        // 재접속한 클라도 대기방으로 돌아갈 수 있게, 게임이 끝나면 항상 ROOM_UPDATED를 보낸다
        verify(sender).sendToRoom("r_1", MessageType.ROOM_UPDATED, updated);
    }

    @Test
    void 정리_후_방이_사라지면_알림을_보내지_않는다() {
        when(lobbyService.memberIdsOf("r_1")).thenReturn(List.of(1L, -1L));
        when(sessionRegistry.isConnected(1L)).thenReturn(false);
        when(lobbyService.removeMembers("r_1", List.of(1L))).thenReturn(false);   // 사람이 없어져 방 삭제

        cleaner.onGameEnded(new GameEndedEvent("r_1"));

        verify(sender, never()).sendToRoom(anyString(), any(), any());
    }

    @Test
    void 정리_중_예외가_나도_밖으로_던지지_않는다() {
        when(lobbyService.memberIdsOf("r_1")).thenThrow(new IllegalStateException("Redis 오류"));

        cleaner.onGameEnded(new GameEndedEvent("r_1"));   // 예외 없이 끝나야 게임 처리에 번지지 않는다

        verify(sender, never()).sendToRoom(anyString(), any(), any());
    }
}