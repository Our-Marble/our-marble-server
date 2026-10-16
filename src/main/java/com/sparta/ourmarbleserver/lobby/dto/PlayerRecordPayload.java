package com.sparta.ourmarbleserver.lobby.dto;

import com.sparta.ourmarbleserver.auth.dto.PlayRecordResponse;

/**
 * PLAYER_RECORD 알림: 내 전적. ROOM_LIST를 보낼 때마다 본인에게 함께 보낸다.
 * <pre>
 * { "type": "PLAYER_RECORD", "nickname": "닉1", "gamesPlayed": 10, "gamesWon": 4 }
 * </pre>
 */
public record PlayerRecordPayload(String nickname, int gamesPlayed, int gamesWon) {

    public static PlayerRecordPayload from(PlayRecordResponse record) {
        return new PlayerRecordPayload(record.nickname(), record.gamesPlayed(), record.gamesWon());
    }
}
