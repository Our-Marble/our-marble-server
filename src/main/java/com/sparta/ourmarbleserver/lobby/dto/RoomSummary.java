package com.sparta.ourmarbleserver.lobby.dto;

import com.sparta.ourmarbleserver.lobby.domain.RoomStatus;

/**
 * 로비 목록에 보이는 방 한 줄. playerCount/maxPlayers로 "2/4"를 표시한다.
 */
public record RoomSummary(
        String roomId,
        String title,
        long hostId,
        int playerCount,
        int maxPlayers,
        RoomStatus status) {
}
