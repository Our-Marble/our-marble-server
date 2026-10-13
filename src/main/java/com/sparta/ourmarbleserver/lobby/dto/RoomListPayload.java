package com.sparta.ourmarbleserver.lobby.dto;

import java.util.List;

/**
 * ROOM_LIST 알림: 대기 중인 방 목록. ROOM_LIST_REQUEST를 보낸 사람에게만 보낸다.
 * <pre>
 * { "type": "ROOM_LIST",
 *   "rooms": [ { "roomId": "r_3", "hostNickname": "철수", "mapId": 1, "playerCount": 2 } ] }
 * </pre>
 * 대기 중(WAITING)인 방만 담고, 방이 없으면 rooms는 빈 배열이다.
 */
public record RoomListPayload(List<RoomSummary> rooms) {

    /** 방 목록에 나오는 방 한 개의 요약. playerCount는 지금 참가한 인원수다. */
    public record RoomSummary(String roomId, String hostNickname, int mapId, int playerCount) {}
}
