package com.sparta.ourmarbleserver.game.event;

import java.util.List;

import com.sparta.ourmarbleserver.game.state.GameState;

/**
 * 게임이 끝났을 때 한 번 발행하는 이벤트. 최종 등수가 정해진 뒤에 나간다.
 * 로비가 받아 방을 WAITING으로 되돌리고, 전적을 올리는 쪽이 results로 승패를 정한다.
 * (GameService가 LobbyService를 직접 부르면 서로를 참조해서 서버가 뜨지 않는다)
 * 게임 상태는 이 이벤트를 받은 뒤 삭제되므로, 결과가 필요한 쪽은 상태를 다시 읽지 말고 results를 쓴다.
 */
public record GameEndedEvent(String roomId, List<PlayerResult> results) {

    /** 플레이어 한 명의 결과. 봇도 순위에는 포함되고 bot으로 구분한다. */
    public record PlayerResult(long playerId, int finalRank, boolean bot) {
    }

    /** 결과 없이 방 번호만으로 만든다. (결과가 필요 없는 쪽과 테스트용) */
    public GameEndedEvent(String roomId) {
        this(roomId, List.of());
    }

    /** 끝난 게임 상태에서 이벤트를 만든다. 최종 등수가 모두 정해진 뒤에 부른다. */
    public static GameEndedEvent of(GameState state) {
        List<PlayerResult> results = state.players().stream()
                .map(player -> new PlayerResult(player.getPlayerId(), player.getFinalRank(), player.isBot()))
                .toList();
        return new GameEndedEvent(state.getRoomId(), results);
    }
}