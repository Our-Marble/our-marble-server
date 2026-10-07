package com.sparta.ourmarbleserver.lobby.dto;

/** ENTER_ROOM 요청: { "topic": "LOBBY", "type": "ENTER_ROOM", "roomId": "1" } */
public record EnterRoomRequest(String roomId) {
}
