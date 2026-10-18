package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.websocket.SessionRegistry;
import com.sparta.ourmarbleserver.lobby.state.InMemoryRoomRepository;
import com.sparta.ourmarbleserver.lobby.state.Room;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class RoomBroadcasterTest {

    private final InMemoryRoomRepository roomRepository = new InMemoryRoomRepository();
    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);
    private final RoomBroadcaster broadcaster = new RoomBroadcaster(roomRepository, sessionRegistry);

    @Test
    void 방_멤버마다_전송한다() {
        Room room = new Room("r_1", 1L, 1, 4);
        room.addMember(1L, true);
        room.addMember(2L, false);
        roomRepository.save(room);

        broadcaster.broadcastJson("r_1", "{\"type\":\"TEST\"}");

        verify(sessionRegistry).send(1L, "{\"type\":\"TEST\"}");
        verify(sessionRegistry).send(2L, "{\"type\":\"TEST\"}");
    }

    @Test
    void 방이_없으면_예외_없이_아무에게도_보내지_않는다() {
        assertThatCode(() -> broadcaster.broadcastJson("없는방", "{}"))
                .doesNotThrowAnyException();

        verifyNoInteractions(sessionRegistry);
    }

    @Test
    void 로비_전송은_방이_없는_접속자에게만_보낸다() {
        Room room = new Room("r_1", 1L, 1, 4);
        room.addMember(1L, true);
        roomRepository.save(room);                         // 1번은 방 안, 2번은 로비
        when(sessionRegistry.connectedPlayerIds()).thenReturn(Set.of(1L, 2L));

        broadcaster.broadcastLobbyJson("{\"type\":\"ROOM_LIST\"}");

        verify(sessionRegistry).send(2L, "{\"type\":\"ROOM_LIST\"}");
        verify(sessionRegistry, never()).send(1L, "{\"type\":\"ROOM_LIST\"}");
    }
}