package com.sparta.ourmarbleserver.lobby.dto;

import java.util.List;

import com.sparta.ourmarbleserver.lobby.domain.RoomStatus;

/**
 * 대기방 상세. ROOM_ENTERED(본인), ROOM_UPDATED(대기방 전원) 알림에 쓴다.
 *
 * @param playerIds 입장 순서대로. 방장이 나가면 그다음 사람이 방장이 된다.
 */
public record RoomDetail(
        String roomId,
        String title,
        long hostId,
        List<Long> playerIds,
        int maxPlayers,
        RoomStatus status) {
}
