package com.sparta.ourmarbleserver.lobby.state;

import com.sparta.ourmarbleserver.lobby.dto.RoomStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 방 하나의 상태. 참가 순서를 지키며 참가자별 준비 여부를 가진다.
 * 규칙 검증(가득 참, 방장 여부 등)은 LobbyService가 하고, 여기서는 상태만 바꾼다.
 */
@Getter
public class Room {
    private final String roomId;
    private final int mapId;
    private final int maxPlayers;

    @Setter
    private long hostId;

    @Setter
    private RoomStatus status = RoomStatus.WAITING;

    /** 참가 순서를 지키는 playerId → 준비 여부. 바깥에서 마음대로 고치지 못하게 getter를 만들지 않는다. */
    @Getter(AccessLevel.NONE)
    private final Map<Long, Boolean> members = new LinkedHashMap<>();

    public Room(String roomId, long hostId, int mapId, int maxPlayers) {
        this.roomId = roomId;
        this.hostId = hostId;
        this.mapId = mapId;
        this.maxPlayers = maxPlayers;
    }

    public boolean hasMember(long playerId) {
        return members.containsKey(playerId);
    }

    public boolean isFull() {
        return members.size() >= maxPlayers;
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    public int size() {
        return members.size();
    }

    public void addMember(long playerId, boolean ready) {
        members.put(playerId, ready);
    }

    public void removeMember(long playerId) {
        members.remove(playerId);
    }

    public boolean isReady(long playerId) {
        return members.getOrDefault(playerId, false);
    }

    public void setReady(long playerId, boolean ready) {
        members.put(playerId, ready);
    }

    public boolean allReady() {
        return members.values().stream().allMatch(ready -> ready);
    }

    /** 참가 순서대로의 playerId. 바깥에서 고쳐도 방 상태는 바뀌지 않는 복사본이다. */
    public List<Long> memberIds() {
        return new ArrayList<>(members.keySet());
    }
}
