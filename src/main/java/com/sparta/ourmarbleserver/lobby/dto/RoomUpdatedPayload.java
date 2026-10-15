package com.sparta.ourmarbleserver.lobby.dto;

import com.sparta.ourmarbleserver.game.state.GameConfig;

import java.util.List;

/**
 * ROOM_UPDATED 알림: 방 상태가 바뀌었다. (입장, 퇴장, 준비, 방장 변경) 방 전원에게 보낸다.
 * <pre>
 * { "topic": "LOBBY", "type": "ROOM_UPDATED",
 *   "room": { "roomId": "r_3", "hostId": 7, "mapId": 1, "status": "WAITING",
 *             "players": [ { "playerId": 1, "nickname": "Alice", "ready": true } ] } }
 * </pre>
 * players의 순서가 참가 순서이고, 게임 시작 시 턴 순서가 된다. 방장은 항상 ready=true다.
 *
 * 방장이 게임 설정이나 최대 인원을 바꾸면(ROOM_CONFIG_REQUEST) 방 전원에게 새 값이 담긴 이 알림이 간다.
 */
public record RoomUpdatedPayload(RoomSnapshot room) {

    /**
     * 방 한 개의 현재 상태. status는 RoomStatus 이름(WAITING, PLAYING)이다.
     * config는 방장이 정한 게임 설정, maxPlayers는 방의 최대 인원이다.
     */
    public record RoomSnapshot(String roomId, long hostId, int mapId, RoomStatus status,
                               GameConfig config, List<PlayerSnapshot> players, int maxPlayers) {

        /** 설정과 최대 인원 없이 만든다. (기본 설정, 최대 인원 4명) */
        public RoomSnapshot(String roomId, long hostId, int mapId, RoomStatus status, List<PlayerSnapshot> players) {
            this(roomId, hostId, mapId, status, GameConfig.defaults().withMapId(mapId), players, GameConfig.MAX_PLAYERS);
        }
    }

    /** 방에 참가한 플레이어. */
    public record PlayerSnapshot(long playerId, String nickname, boolean ready) {
    }
}