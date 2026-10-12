package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.global.websocket.SessionRegistry;
import com.sparta.ourmarbleserver.lobby.state.Room;
import com.sparta.ourmarbleserver.lobby.state.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

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
        Optional<Room> room = roomRepository.findById(roomId);
        if (room.isEmpty()) {
            log.warn("[Broadcast] 방 없음, 전송 생략: room={}", roomId);
            return;
        }

        for(long memberId : room.get().memberIds()) {
            sessionRegistry.send(memberId, jsonMessage);
        }
    }
}
