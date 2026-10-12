package com.sparta.ourmarbleserver.lobby.dto;

/**
 * GAME_STARTED 알림: 게임이 시작됐다. 클라는 이 알림으로 게임 화면으로 넘어간다. 방 전원에게 보낸다.
 * <pre>
 * { "topic": "LOBBY", "type": "GAME_STARTED", "roomId": "r_3" }
 * </pre>
 */
public record GameStartedPayload(String roomId) {
}