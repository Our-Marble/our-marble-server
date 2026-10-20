package com.sparta.ourmarbleserver.lobby.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sparta.ourmarbleserver.bot.service.BotService;
import com.sparta.ourmarbleserver.game.state.GameConfig;

import java.util.List;

/**
 * ROOM_UPDATED 알림: 방 상태가 바뀌었다. (입장, 퇴장, 준비, 방장 변경) 방 전원에게 보낸다.
 * <pre>
 * { "topic": "LOBBY", "type": "ROOM_UPDATED",
 *   "room": { "roomId": "r_3", "hostId": 7, "mapId": 1, "status": "WAITING",
 *       "players": [
 *         { "playerId": 1,  "nickname": "닉1", "ready": false, "bot": false },
 *        { "playerId": -1, "nickname": "봇1", "ready": true,  "bot": true }
 *      ] }
 * }
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

    /**
     * 방에 참가한 플레이어. bot이 true면 서버가 대신 움직이는 봇이다. (봇은 항상 ready=true)
     * portraitId는 그 사람이 고른 초상화 번호(0~7)다. 고르지 않았거나 봇이면 이 필드는 빠진다.
     */
    public record PlayerSnapshot(long playerId, String nickname, boolean ready, boolean bot,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) Integer portraitId) {

        /** 초상화 없이 만든다. */
        public PlayerSnapshot(long playerId, String nickname, boolean ready, boolean bot) {
            this(playerId, nickname, ready, bot, null);
        }

        /** bot 값을 안 주는 기존 호출용. 번호로 봇인지 정한다. (음수면 봇) */
        public PlayerSnapshot(long playerId, String nickname, boolean ready) {
            this(playerId, nickname, ready, BotService.isBotId(playerId), null);
        }
    }
}