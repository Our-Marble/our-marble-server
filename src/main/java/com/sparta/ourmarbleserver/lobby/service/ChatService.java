package com.sparta.ourmarbleserver.lobby.service;

import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.LobbyErrorCode;
import com.sparta.ourmarbleserver.lobby.dto.RoomInfo;
import com.sparta.ourmarbleserver.lobby.dto.RoomStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 로비 채팅 도메인 서비스. 채팅을 보내도 되는지 판단하고, 보낼 방을 알려 준다.
 * 실제 전송(CHAT_BROADCAST)은 요청을 받는 쪽(ChatMessageHandler)이 한다.
 * 글자 수 제한과 도배 방지는 하지 않고, 지난 채팅도 저장하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final LobbyService lobbyService;

    /**
     * 이 플레이어의 채팅을 보낼 방 id. content가 null이거나 공백뿐이면 보낼 것이 없어서 empty.
     * 방에 없으면 NOT_IN_ROOM, 방이 대기 중(WAITING)이 아니면(게임 중) INVALID_STATE.
     * 요청에는 roomId가 실리지 않아서 보낸 사람의 방을 이걸로 찾는다.
     */
    public Optional<String> roomIdToSend(long playerId, String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        RoomInfo room = lobbyService.findRoomByPlayer(playerId);   // 방에 없으면 NOT_IN_ROOM
        if (room.status() != RoomStatus.WAITING) {
            throw new GameException(LobbyErrorCode.INVALID_STATE);
        }
        return Optional.of(room.roomId());
    }
}
