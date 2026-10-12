package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.websocket.SessionRegistry;
import com.sparta.ourmarbleserver.lobby.state.InMemoryRoomRepository;
import com.sparta.ourmarbleserver.lobby.state.Room;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
}