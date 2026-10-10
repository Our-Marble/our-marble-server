package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.websocket.SessionRegistry;
import com.sparta.ourmarbleserver.lobby.state.Room;
import com.sparta.ourmarbleserver.lobby.state.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RoomBroadcaster {

    private final RoomRepository roomRepository;
    private final SessionRegistry sessionRegistry;

    public void broadcastJson(
            String roomId,
            String jsonMessage
    ) {
        Room room = roomRepository.findById(roomId).orElseThrow();

        for(long memberId : room.memberIds())
        {
            sessionRegistry.send(memberId, jsonMessage);
        }
    }
}
