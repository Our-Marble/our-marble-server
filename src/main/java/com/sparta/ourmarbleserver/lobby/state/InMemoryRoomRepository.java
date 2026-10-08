package com.sparta.ourmarbleserver.lobby.state;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 서버 메모리에 방 상태를 보관하는 구현. */
@Component
public class InMemoryRoomRepository implements RoomRepository {
    private final Map<String, Room> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Room> findById(String roomId) {
        return Optional.ofNullable(store.get(roomId));
    }

    @Override
    public List<Room> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public void save(Room room) {
        store.put(room.getRoomId(), room);
    }

    @Override
    public void deleteById(String roomId) {
        store.remove(roomId);
    }
}
