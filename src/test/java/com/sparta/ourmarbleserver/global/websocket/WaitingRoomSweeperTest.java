package com.sparta.ourmarbleserver.global.websocket;

import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.lobby.dto.RoomUpdatedPayload;
import com.sparta.ourmarbleserver.lobby.service.LobbyService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WaitingRoomSweeperTest {

    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);
    private final LobbyService lobbyService = mock(LobbyService.class);
    private final WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
    private final WaitingRoomSweeper sweeper = new WaitingRoomSweeper(sessionRegistry, lobbyService, sender);

    private final RoomUpdatedPayload updated = new RoomUpdatedPayload(null);

    @Test
    void 접속_기록이_없는_사람만_내보내고_남은_사람에게_알린다() {
        when(lobbyService.waitingRoomIds()).thenReturn(List.of("r_1"));
        when(lobbyService.memberIdsOf("r_1")).thenReturn(List.of(1L, 2L, -1L));
        when(sessionRegistry.isConnected(1L)).thenReturn(true);   // 2는 접속 기록 없음, -1은 봇
        when(lobbyService.removeMembers("r_1", List.of(2L))).thenReturn(true);
        when(lobbyService.roomUpdated("r_1")).thenReturn(updated);

        sweeper.sweep();

        verify(lobbyService).removeMembers("r_1", List.of(2L));
        verify(sender).sendToRoom("r_1", MessageType.ROOM_UPDATED, updated);
    }

    @Test
    void 모두_접속_중이면_아무것도_하지_않는다() {
        when(lobbyService.waitingRoomIds()).thenReturn(List.of("r_1"));
        when(lobbyService.memberIdsOf("r_1")).thenReturn(List.of(1L, -1L));
        when(sessionRegistry.isConnected(1L)).thenReturn(true);

        sweeper.sweep();

        verify(lobbyService, never()).removeMembers(anyString(), anyCollection());
        verify(sender, never()).sendToRoom(anyString(), any(), any());
    }

    @Test
    void 정리_후_방이_지워졌으면_알리지_않는다() {
        when(lobbyService.waitingRoomIds()).thenReturn(List.of("r_1"));
        when(lobbyService.memberIdsOf("r_1")).thenReturn(List.of(2L));
        when(lobbyService.removeMembers("r_1", List.of(2L))).thenReturn(false);

        sweeper.sweep();

        verify(sender, never()).sendToRoom(anyString(), any(), any());
    }

    @Test
    void 한_방에서_실패해도_다음_방은_청소한다() {
        when(lobbyService.waitingRoomIds()).thenReturn(List.of("r_1", "r_2"));
        when(lobbyService.memberIdsOf("r_1")).thenThrow(new RuntimeException("Redis 오류"));
        when(lobbyService.memberIdsOf("r_2")).thenReturn(List.of(5L));
        when(lobbyService.removeMembers("r_2", List.of(5L))).thenReturn(false);

        sweeper.sweep();

        verify(lobbyService).removeMembers("r_2", List.of(5L));
    }
}