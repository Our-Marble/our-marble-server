package com.sparta.ourmarbleserver.lobby.state;

import com.sparta.ourmarbleserver.game.state.GameConfig;
import com.sparta.ourmarbleserver.lobby.dto.RoomStatus;

import java.util.List;

/**
 * Redis에 저장하는 방 모양. Room은 멤버 Map을 밖에 열지 않으므로 저장용 record를 따로 둔다.
 * members는 참가 순서를 지킨다. (게임 시작 시 턴 순서가 된다)
 * Room에 필드를 추가하면 여기에도 추가해야 한다. (StoredRoomTest가 잡아 준다)
 */
public record StoredRoom(String roomId, long hostId, int mapId, int maxPlayers,
                         RoomStatus status, GameConfig config, List<Member> members) {

    public record Member(long playerId, String nickname, boolean ready) {
    }

    public static StoredRoom from(Room room) {
        List<Member> members = room.memberIds().stream()
                .map(id -> new Member(id, room.nicknameOf(id), room.isReady(id)))
                .toList();
        return new StoredRoom(room.getRoomId(), room.getHostId(), room.getMapId(),
                room.getMaxPlayers(), room.getStatus(), room.getConfig(), members);
    }

    public Room toRoom() {
        Room room = new Room(roomId, hostId, mapId, maxPlayers);
        room.setStatus(status);
        room.setConfig(config);
        for (Member member : members) {
            room.addMember(member.playerId(), member.ready());
            room.setNickname(member.playerId(), member.nickname());
        }
        return room;
    }
}