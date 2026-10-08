package com.sparta.ourmarbleserver.lobby.state;

import java.util.List;
import java.util.Optional;

/** 방 상태 저장소. 지금은 메모리 구현만 있고, 나중에 다른 저장소로 바꿔 끼울 수 있다. */
public interface RoomRepository {

    Optional<Room> findById(String roomId);

    List<Room> findAll();

    void save(Room room);

    void deleteById(String roomId);
}

