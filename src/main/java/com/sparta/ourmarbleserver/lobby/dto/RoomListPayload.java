package com.sparta.ourmarbleserver.lobby.dto;

import java.util.List;

/** ROOM_LIST 알림: 로비에 들어온 플레이어에게 보내는 방 목록 전체. */
public record RoomListPayload(List<RoomSummary> rooms) {
}
