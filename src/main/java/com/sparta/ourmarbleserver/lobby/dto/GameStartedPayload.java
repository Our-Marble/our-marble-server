package com.sparta.ourmarbleserver.lobby.dto;

import java.util.List;

/**
 * GAME_STARTED 알림: 게임창 전환 신호. game/room/{id} 전원에게 보낸다.
 * 초기 게임 상태 필드는 게임 로직(3-2)과 맞춰 추가한다.
 */
public record GameStartedPayload(String roomId, List<Long> playerOrder) {
}
