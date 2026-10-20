package com.sparta.ourmarbleserver.global.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * CONNECTED 알림: 웹소켓 연결 직후 본인에게 보내는 "너는 몇 번 플레이어다".
 * 클라는 playerId를 직접 정하지 않고 이 값을 LocalPlayerId로 저장한다.
 * portraitId는 저장된 내 초상화 번호(0~7)다. 고른 적이 없으면 이 필드는 빠진다.
 * <pre>
 * { type": "CONNECTED", "playerId": 7, "nickname": "우리서버가짱", "portraitId": 3 }
 * </pre>
 */
public record ConnectedPayload(long playerId, String nickname,
                               @JsonInclude(JsonInclude.Include.NON_NULL) Integer portraitId) {

    /** 초상화 없이 만든다. (고른 적이 없는 계정) */
    public ConnectedPayload(long playerId, String nickname) {
        this(playerId, nickname, null);
    }
}
