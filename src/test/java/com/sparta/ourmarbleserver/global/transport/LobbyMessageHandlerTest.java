package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.game.state.GameConfig;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.CommonErrorCode;
import com.sparta.ourmarbleserver.global.protocol.LobbyErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.websocket.WebSocketMessageSender;
import com.sparta.ourmarbleserver.lobby.dto.RoomInfo;
import com.sparta.ourmarbleserver.lobby.dto.RoomListPayload;
import com.sparta.ourmarbleserver.lobby.dto.RoomStatus;
import com.sparta.ourmarbleserver.lobby.dto.RoomUpdatedPayload;
import com.sparta.ourmarbleserver.lobby.service.LobbyService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;

class LobbyMessageHandlerTest {

    private final LobbyService lobbyService = mock(LobbyService.class);
    private final WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LobbyScreenSender lobbyScreenSender = mock(LobbyScreenSender.class);
    private final LobbyMessageHandler handler = new LobbyMessageHandler(lobbyService, sender, lobbyScreenSender, objectMapper);
    private final RoomUpdatedPayload updated = new RoomUpdatedPayload(null);
    private final RoomListPayload list = new RoomListPayload(List.of());

    /** 기본값과 모두 다른 설정. */
    private static final GameConfig NEW_CONFIG = new GameConfig(1_000_000L, 20_000L, 50_000L, 10, 2, 3, 30);

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
    void 나가면_본인에게_ROOM_LEFT와_로비_화면을_보내고_방이_없으면_방_전원에게는_보내지_않는다() {
        when(lobbyService.roomIdOf(1L)).thenReturn("r_1");
        when(lobbyService.leave("r_1", 1L)).thenReturn(false);

        handler.handle(MessageType.LEAVE_ROOM, 1L, objectMapper.createObjectNode());

        InOrder order = inOrder(sender, lobbyScreenSender);
        order.verify(sender).sendToPlayer(1L, MessageType.ROOM_LEFT, null);
        order.verify(lobbyScreenSender).send(1L);
        verify(sender, never()).sendToRoom(anyString(), any(), any());
        verify(sender, never()).sendToLobby(any(), any());
    }

    @Test
    void 새로고침하면_로비_화면을_보낸다() {
        handler.handle(MessageType.ROOM_LIST_REQUEST, 1L, objectMapper.createObjectNode());

        verify(lobbyScreenSender).send(1L);
    }

    @Test
    void 방_번호_없이_입장하면_INVALID_MESSAGE() {
        assertThatThrownBy(() -> handler.handle(MessageType.ENTER_ROOM, 1L, objectMapper.createObjectNode()))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(CommonErrorCode.INVALID_MESSAGE));
        verify(lobbyService, never()).join(anyString(), eq(1L));
    }

    // ===== 방장의 게임 설정 변경 (ROOM_CONFIG_REQUEST) =====

    @Test
    void 방장이_설정을_바꾸면_방_전원에게_ROOM_UPDATED를_보낸다() {
        when(lobbyService.roomIdOf(1L)).thenReturn("r_1");
        when(lobbyService.roomUpdated("r_1")).thenReturn(updated);

        handler.handle(MessageType.ROOM_CONFIG_REQUEST, 1L, configBody(NEW_CONFIG, 3, 1));

        InOrder order = inOrder(lobbyService, sender);
        order.verify(lobbyService).setRoomConfig("r_1", 1L, NEW_CONFIG, 3, 1);       // 설정·최대 인원·봇 수를 그대로 전달
        order.verify(sender).sendToRoom("r_1", MessageType.ROOM_UPDATED, updated);   // 바꾼 뒤에 방 전원에게 알림
        verify(sender, never()).sendToPlayer(anyLong(), any(), any());
    }

    @Test
    void 봇_수나_최대_인원을_빼고_보내면_형식_오류로_거절하고_설정을_바꾸지_않는다() {
        when(lobbyService.roomIdOf(1L)).thenReturn("r_1");

        ObjectNode noBotCount = configBody(NEW_CONFIG, 3, 1);
        noBotCount.remove("botCount");
        ObjectNode noMaxPlayers = configBody(NEW_CONFIG, 3, 1);
        noMaxPlayers.remove("maxPlayers");

        // int 필드가 없으면 Jackson 3은 0을 넣지 않고 예외를 던진다 → MainWebSocketHandler가 COMMON_ERROR / INVALID_MESSAGE로 응답
        assertThatThrownBy(() -> handler.handle(MessageType.ROOM_CONFIG_REQUEST, 1L, noBotCount))
                .isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> handler.handle(MessageType.ROOM_CONFIG_REQUEST, 1L, noMaxPlayers))
                .isInstanceOf(JacksonException.class);

        verify(lobbyService, never()).setRoomConfig(anyString(), anyLong(), any(), anyInt(), anyInt());
        verify(sender, never()).sendToRoom(anyString(), any(), any());
    }

    @Test
    void 방장이_아니면_오류가_그대로_올라가고_알림은_보내지_않는다() {
        when(lobbyService.roomIdOf(2L)).thenReturn("r_1");
        doThrow(new GameException(LobbyErrorCode.NOT_HOST))
                .when(lobbyService).setRoomConfig("r_1", 2L, NEW_CONFIG, 3, 1);

        assertThatThrownBy(() -> handler.handle(MessageType.ROOM_CONFIG_REQUEST, 2L, configBody(NEW_CONFIG, 3, 1)))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(LobbyErrorCode.NOT_HOST));
        verify(sender, never()).sendToRoom(anyString(), any(), any());
    }

    /** {"config": {...}, "maxPlayers": n, "botCount": b} 모양의 요청 본문. type은 핸들러에 따로 넘기므로 넣지 않는다. */
    private ObjectNode configBody(GameConfig config, int maxPlayers, int botCount) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("config", objectMapper.valueToTree(config));
        body.put("maxPlayers", maxPlayers);
        body.put("botCount", botCount);
        return body;
    }

    // ===== 초상화 (SET_PORTRAIT) =====

    @Test
    void 대기_중인_방에서_초상화를_바꾸면_방_전원에게_ROOM_UPDATED를_보낸다() {
        when(lobbyService.setPortrait(1L, 3)).thenReturn(java.util.Optional.of("r_1"));
        when(lobbyService.roomUpdated("r_1")).thenReturn(updated);

        handler.handle(MessageType.SET_PORTRAIT, 1L, objectMapper.createObjectNode().put("portraitId", 3));

        verify(sender).sendToRoom("r_1", MessageType.ROOM_UPDATED, updated);
        verify(sender, never()).sendToPlayer(anyLong(), any(), any());   // 요청자에게 따로 응답하지 않는다
    }

    @Test
    void 방에_없거나_게임_중이면_저장만_하고_아무에게도_보내지_않는다() {
        when(lobbyService.setPortrait(1L, 3)).thenReturn(java.util.Optional.empty());

        handler.handle(MessageType.SET_PORTRAIT, 1L, objectMapper.createObjectNode().put("portraitId", 3));

        verify(lobbyService).setPortrait(1L, 3);
        verify(sender, never()).sendToRoom(anyString(), any(), any());
        verify(sender, never()).sendToPlayer(anyLong(), any(), any());
    }

    @Test
    void 번호가_범위_밖이면_서비스의_INVALID_PORTRAIT_오류가_그대로_올라간다() {
        when(lobbyService.setPortrait(1L, 8)).thenThrow(new GameException(LobbyErrorCode.INVALID_PORTRAIT));

        assertThatThrownBy(() -> handler.handle(MessageType.SET_PORTRAIT, 1L, objectMapper.createObjectNode().put("portraitId", 8)))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(LobbyErrorCode.INVALID_PORTRAIT));
        verify(sender, never()).sendToRoom(anyString(), any(), any());
    }

    @Test
    void 번호가_없으면_INVALID_MESSAGE() {
        assertThatThrownBy(() -> handler.handle(MessageType.SET_PORTRAIT, 1L, objectMapper.createObjectNode()))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(CommonErrorCode.INVALID_MESSAGE));
        verify(lobbyService, never()).setPortrait(anyLong(), anyInt());
    }
}
