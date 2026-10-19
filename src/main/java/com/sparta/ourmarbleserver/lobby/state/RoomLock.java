package com.sparta.ourmarbleserver.lobby.state;

import java.util.function.Supplier;

/**
 * 방 하나를 서버 사이에서 잠그는 락. 방을 "읽고 → 고치고 → 저장"하는 동안 다른 서버가 끼어들지 못하게 한다.
 * 서버는 RedisRoomLock, 테스트는 InMemoryRoomLock을 쓴다.
 */
public interface RoomLock {

    /** 방을 잠근 채 action을 실행하고 결과를 돌려준다. 정해진 시간 안에 못 잠그면 ROOM_BUSY. */
    <T> T call(String roomId, Supplier<T> action);

    default void run(String roomId, Runnable action) {
        call(roomId, () -> {
            action.run();
            return null;
        });
    }
}