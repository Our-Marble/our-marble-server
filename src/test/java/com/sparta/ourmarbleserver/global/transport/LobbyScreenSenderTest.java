package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.auth.dto.PlayRecordResponse;
import com.sparta.ourmarbleserver.auth.service.PlayerService;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.websocket.WebSocketMessageSender;
import com.sparta.ourmarbleserver.lobby.dto.PlayerRecordPayload;
import com.sparta.ourmarbleserver.lobby.dto.RoomListPayload;
import com.sparta.ourmarbleserver.lobby.service.LobbyService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LobbyScreenSenderTest {

    private final LobbyService lobbyService = mock(LobbyService.class);
    private final PlayerService playerService = mock(PlayerService.class);
    private final WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
    private final LobbyScreenSender lobbyScreenSender = new LobbyScreenSender(lobbyService, playerService, sender);

    private final RoomListPayload list = new RoomListPayload(List.of());

    @Test
    void 방_목록과_내_전적을_차례로_보낸다() {
        when(lobbyService.roomList()).thenReturn(list);
        when(playerService.findMyPlayRecord(1L)).thenReturn(new PlayRecordResponse("닉1", 10, 4));

        lobbyScreenSender.send(1L);

        InOrder order = inOrder(sender);
        order.verify(sender).sendToPlayer(1L, MessageType.ROOM_LIST, list);
        order.verify(sender).sendToPlayer(1L, MessageType.PLAYER_RECORD, new PlayerRecordPayload("닉1", 10, 4));
    }

    @Test
    void 전적_조회가_실패해도_방_목록은_보내고_예외를_올리지_않는다() {
        when(lobbyService.roomList()).thenReturn(list);
        when(playerService.findMyPlayRecord(1L)).thenThrow(new RuntimeException("DB 오류"));

        assertThatCode(() -> lobbyScreenSender.send(1L)).doesNotThrowAnyException();

        verify(sender).sendToPlayer(1L, MessageType.ROOM_LIST, list);
        verify(sender, never()).sendToPlayer(eq(1L), eq(MessageType.PLAYER_RECORD), any());
    }
}