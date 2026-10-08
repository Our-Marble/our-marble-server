package com.sparta.ourmarbleserver.global.protocol;

/**
 * CONNECTED 알림: 웹소켓 연결 직후 본인에게 보내는 "너는 몇 번 플레이어다".
 * 클라는 playerId를 직접 정하지 않고 이 값을 LocalPlayerId로 저장한다.
 * <pre>
 * { "topic": "LOBBY", "type": "CONNECTED", "playerId": 7 }
 * </pre>
 */
public record ConnectedPayload(long playerId) {
}
