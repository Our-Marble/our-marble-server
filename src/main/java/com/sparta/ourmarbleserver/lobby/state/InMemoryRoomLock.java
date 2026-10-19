package com.sparta.ourmarbleserver.lobby.state;

import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.LobbyErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 테스트용 락. 실제로 잠그지 않고, 어느 방을 잠갔는지 기록만 한다. busy면 ROOM_BUSY를 던진다. */
public class InMemoryRoomLock implements RoomLock {

    private final List<String> lockedRooms = new ArrayList<>();
    private boolean busy;
    private boolean held;

    @Override
    public <T> T call(String roomId, Supplier<T> action) {
        if (busy) {
            throw new GameException(LobbyErrorCode.ROOM_BUSY);
        }
        lockedRooms.add(roomId);
        held = true;
        try {
            return action.get();
        } finally {
            held = false;
        }
    }

    /** 지금 락을 잡고 있는 중인지. action 안에서 부르면 true다. */
    public boolean isHeld() {
        return held;
    }

    public List<String> lockedRooms() {
        return lockedRooms;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }
}