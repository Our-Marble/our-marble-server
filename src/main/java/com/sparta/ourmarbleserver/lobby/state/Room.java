package com.sparta.ourmarbleserver.lobby.state;

import com.sparta.ourmarbleserver.game.state.GameConfig;
import com.sparta.ourmarbleserver.lobby.dto.RoomStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * 방 하나의 상태. 참가 순서를 지키며 참가자별 준비 여부를 가진다.
 * 규칙 검증(가득 참, 방장 여부 등)은 LobbyService가 하고, 여기서는 상태만 바꾼다.
 */
@Getter
public class Room {
    private final String roomId;

    @Setter
    private long hostId;

    private final int mapId;

    @Setter // 최대 인원 2~4명. 방장이 대기 중에 변경 가능
    private int maxPlayers;

    @Setter
    private RoomStatus status = RoomStatus.WAITING;

    /** 참가 순서를 지키는 playerId → 준비 여부. 바깥에서 마음대로 고치지 못하게 getter를 만들지 않는다. */
    @Getter(AccessLevel.NONE)
    private final Map<Long, Boolean> members = new LinkedHashMap<>();

    /** playerId → 닉네임. 입장할 때 한 번 읽어서 방에 저장해 두고, ROOM_UPDATED를 만들 때 쓴다. */
    @Getter(AccessLevel.NONE)
    private final Map<Long, String> nicknames = new HashMap<>();

    /** playerId → 초상화 번호(0~7). 입장할 때 계정에서 읽어 방에 저장해 두고, ROOM_UPDATED를 만들 때 쓴다. 고르지 않았거나 봇이면 항목이 없다. */
    @Getter(AccessLevel.NONE)
    private final Map<Long, Integer> portraits = new HashMap<>();

    /** 게임 설정. 방장이 바꿀 수 있고, 게임이 시작될 때 startGame에 넘긴다. 맵 번호도 여기에 있다. */
    @Setter
    private GameConfig config;

    public Room(String roomId, long hostId, int mapId, int maxPlayers) {
        this.roomId = roomId;
        this.hostId = hostId;
        this.mapId = mapId;
        this.maxPlayers = maxPlayers;
        this.config = GameConfig.defaults().withMapId(mapId);
    }

    /** 맵 번호. 게임 설정(config)에 들어 있는 값이다. */
    public int getMapId() { return config.mapId(); }

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
        nicknames.remove(playerId);
        portraits.remove(playerId);
    }

    public void setNickname(long playerId, String nickname) {
        nicknames.put(playerId, nickname);
    }

    /** 초상화 번호를 저장한다. null이면 지운다. (고르지 않은 상태) */
    public void setPortrait(long playerId, Integer portraitId) {
        if (portraitId == null) {
            portraits.remove(playerId);
        } else {
            portraits.put(playerId, portraitId);
        }
    }

    /** 저장된 초상화 번호. 없으면 null. */
    public Integer portraitOf(long playerId) {
        return portraits.get(playerId);
    }

    /** 저장된 닉네임. 없으면 빈 문자열. */
    public String nicknameOf(long playerId) {
        return nicknames.getOrDefault(playerId, "");
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
