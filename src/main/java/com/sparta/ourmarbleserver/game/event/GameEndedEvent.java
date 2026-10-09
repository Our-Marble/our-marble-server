package com.sparta.ourmarbleserver.game.event;

/**
 * 게임이 끝났을 때 한 번 발행하는 이벤트. 최종 등수가 정해진 뒤에 나간다.
 * 로비가 받아서 방을 지운다. (GameService가 LobbyService를 직접 부르면 서로를 참조해서 서버가 뜨지 않는다)
 */
public record GameEndedEvent(String roomId) {
}
