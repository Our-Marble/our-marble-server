package com.sparta.ourmarbleserver.global.protocol;

/**
 * 구독 주소. 같은 주소를 구독한 플레이어에게만 메시지가 간다.
 * 주소는 글자가 정확히 같아야 연결되며, 상위 주소로 보내도 하위 주소 구독자는 받지 않는다.
 * 나중에 STOMP로 옮길 때 그대로 쓸 수 있도록 STOMP 경로 모양으로 맞춰 둔다.
 */
public final class TopicPath {

    /**
     * 로비. 로그인 직후, 대기방에서 나왔을 때 구독하며 "로비에 있다"는 표시로 쓴다.
     * 방 목록은 전송량을 줄이려고 방송하지 않고 요청한 사람에게만 보낸다(ROOM_LIST).
     * 지금은 이 주소로 방송하는 메시지가 없고, 추후 공지 등 로비 전체 알림에 쓴다.
     */
    public static final String LOBBY_MAIN = "/topic/lobby/main";

    private static final String LOBBY_ROOM_PREFIX = "/topic/lobby/room/";
    private static final String GAME_ROOM_PREFIX = "/topic/game/room/";

    private TopicPath() {
    }

    /** 대기방. 입장·퇴장, 방장 변경 알림을 받는다. */
    public static String lobbyRoom(String roomId) {
        return LOBBY_ROOM_PREFIX + roomId;
    }

    /** 게임방. 게임 진행 알림을 받는다. */
    public static String gameRoom(String roomId) {
        return GAME_ROOM_PREFIX + roomId;
    }
}
