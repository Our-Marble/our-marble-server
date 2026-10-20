package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.game.dto.GameMessage;
import com.sparta.ourmarbleserver.game.dto.GameStateSyncPayload;
import com.sparta.ourmarbleserver.game.dto.GameStateSyncPayload.GameSnapshot;
import com.sparta.ourmarbleserver.game.service.GameService;
import com.sparta.ourmarbleserver.game.state.GameConfig;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.websocket.WebSocketMessageSender;
import com.sparta.ourmarbleserver.lobby.dto.RoomInfo;
import com.sparta.ourmarbleserver.lobby.dto.RoomStatus;
import com.sparta.ourmarbleserver.lobby.service.LobbyService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** GAME_STATE_REQUEST를 받았을 때 GAME_STATE_SYNC에 방 정보(hostId, maxPlayers)가 붙는지 확인한다. */
class GameMessageHandlerStateSyncTest {

    private final GameService gameService = mock(GameService.class);
    private final WebSocketMessageSender sender = mock(WebSocketMessageSender.class);
    private final LobbyService lobbyService = mock(LobbyService.class);
    private final GameMessageHandler handler = new GameMessageHandler(gameService, sender, lobbyService);

    private static GameStateSyncPayload syncPayload() {
        GameSnapshot snapshot = new GameSnapshot("r_1", null, null, 5, 2, 7L, 0L, "AWAITING_ROLL", false, 0,
                List.of(7L, 3L), List.of(), List.of(), GameConfig.defaults());
        return new GameStateSyncPayload(snapshot);
    }

    private GameSnapshot sentSnapshot() {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(sender).sendToPlayer(eq(3L), eq(MessageType.GAME_STATE_SYNC), payload.capture());
        return ((GameStateSyncPayload) payload.getValue()).gameState();
    }

    private void request() {
        handler.handle(MessageType.GAME_STATE_REQUEST, 3L, new ObjectMapper().createObjectNode());
    }

    @Test
    void 방이_있으면_방장과_최대_인원이_붙는다() {
        when(gameService.syncState(3L)).thenReturn(Optional.of(new GameMessage(MessageType.GAME_STATE_SYNC, syncPayload())));
        when(lobbyService.findRoomInfo("r_1")).thenReturn(
                Optional.of(new RoomInfo("r_1", 7L, 1, 4, List.of(), RoomStatus.PLAYING)));

        request();

        GameSnapshot sent = sentSnapshot();
        assertThat(sent.hostId()).isEqualTo(7L);
        assertThat(sent.maxPlayers()).isEqualTo(4);
        assertThat(sent.roomId()).isEqualTo("r_1");        // 나머지 내용은 그대로
        assertThat(sent.currentPlayerId()).isEqualTo(7L);
    }

    @Test
    void 방을_못_찾으면_방_정보_없이_그대로_보낸다() {
        when(gameService.syncState(3L)).thenReturn(Optional.of(new GameMessage(MessageType.GAME_STATE_SYNC, syncPayload())));
        when(lobbyService.findRoomInfo("r_1")).thenReturn(Optional.empty());

        request();

        GameSnapshot sent = sentSnapshot();
        assertThat(sent.hostId()).isNull();
        assertThat(sent.maxPlayers()).isNull();
        assertThat(sent.roomId()).isEqualTo("r_1");
    }

    @Test
    void 진행_중인_게임이_없으면_payload_없이_보내고_방을_조회하지_않는다() {
        when(gameService.syncState(3L)).thenReturn(Optional.empty());

        request();

        verify(sender).sendToPlayer(3L, MessageType.GAME_STATE_SYNC, null);
        org.mockito.Mockito.verifyNoInteractions(lobbyService);
    }
}
