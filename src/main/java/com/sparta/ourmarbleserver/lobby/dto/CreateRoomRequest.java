package com.sparta.ourmarbleserver.lobby.dto;

/** CREATE_ROOM 요청: { "topic": "LOBBY", "type": "CREATE_ROOM", "title": "한판" } */
public record CreateRoomRequest(String title) {
}
