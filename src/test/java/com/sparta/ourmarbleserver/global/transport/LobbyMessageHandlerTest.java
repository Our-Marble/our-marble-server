package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.websocket.WebSocketMessageSender;
import com.sparta.ourmarbleserver.lobby.dto.RoomInfo;
import com.sparta.ourmarbleserver.lobby.dto.RoomListPayload;
import com.sparta.ourmarbleserver.lobby.dto.RoomStatus;
import com.sparta.ourmarbleserver.lobby.dto.RoomUpdatedPayload;
import com.sparta.ourmarbleserver.lobby.service.LobbyService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LobbyMessageHandlerTest {

    private final LobbyService lobbyService = mock(LobbyService.class);
    private final WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LobbyMessageHandler handler = new LobbyMessageHandler(lobbyService, sender, objectMapper);

    private final RoomUpdatedPayload updated = new RoomUpdatedPayload(null);
    private final RoomListPayload list = new RoomListPayload(List.of());

    @Test
    void 방을_만들면_본인에게_입장_방_전원에게_상태를_알린다() {
        when(lobbyService.createRoom(1L))
                .thenReturn(new RoomInfo("r_1", 1L, 1, 4, List.of(), RoomStatus.WAITING));
        when(lobbyService.roomUpdated("r_1")).thenReturn(updated);

        handler.handle(MessageType.CREATE_ROOM, 1L, objectMapper.createObjectNode());

        InOrder order = inOrder(sender);
        order.verify(sender).sendToPlayer(1L, MessageType.ROOM_ENTERED, null);
        order.verify(sender).sendToRoom("r_1", MessageType.ROOM_UPDATED, updated);
        verify(sender, never()).sendToLobby(any(), any());   // 방 목록은 자동으로 보내지 않는다
    }

    @Test
    void 나가면_본인에게_ROOM_LEFT와_최신_목록을_보내고_방이_없으면_방_전원에게는_보내지_않는다() {
        when(lobbyService.roomIdOf(1L)).thenReturn("r_1");
        when(lobbyService.leave("r_1", 1L)).thenReturn(false);
        when(lobbyService.roomList()).thenReturn(list);

        handler.handle(MessageType.LEAVE_ROOM, 1L, objectMapper.createObjectNode());

        InOrder order = inOrder(sender);
        order.verify(sender).sendToPlayer(1L, MessageType.ROOM_LEFT, null);
        order.verify(sender).sendToPlayer(1L, MessageType.ROOM_LIST, list);
        verify(sender, never()).sendToRoom(anyString(), any(), any());
        verify(sender, never()).sendToLobby(any(), any());
    }

    @Test
    void 방_번호_없이_입장하면_INVALID_MESSAGE() {
        assertThatThrownBy(() -> handler.handle(MessageType.ENTER_ROOM, 1L, objectMapper.createObjectNode()))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_MESSAGE));
        verify(lobbyService, never()).join(anyString(), eq(1L));
    }
}