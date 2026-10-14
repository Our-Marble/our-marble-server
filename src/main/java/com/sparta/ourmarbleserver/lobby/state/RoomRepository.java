package com.sparta.ourmarbleserver.lobby.state;

import java.util.List;
import java.util.Optional;

/** 방 상태 저장소. 서버는 RedisRoomRepository, 테스트는 InMemoryRoomRepository를 쓴다. */
public interface RoomRepository {

    Optional<Room> findById(String roomId);

    List<Room> findAll();

    void save(Room room);

    void deleteById(String roomId);

    Optional<String> findRoomIdByPlayerId(long playerId);

    // 새 방 id 발급 (서버가 여러 대여도 겹치지 않아야 한다)
    String nextRoomId();
}

