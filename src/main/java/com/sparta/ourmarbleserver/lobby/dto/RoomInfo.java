package com.sparta.ourmarbleserver.lobby.dto;

import java.util.List;

/**
 * 방 한 개의 정보. 컨트롤러가 그대로 응답으로 내보낸다.
 * players의 순서가 참가 순서이고, 게임 시작 시 턴 순서가 된다.
 */
public record RoomInfo(String roomId, long hostId, int mapId, int maxPlayers,
                       List<RoomPlayer> players, RoomStatus status) {
}
