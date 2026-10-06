package com.sparta.ourmarbleserver.game.state;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** 서버 메모리에 방 상태를 보관하는 구현. */
@Component
public class InMemoryGameStateRepository implements GameStateRepository {

    private final Map<String, GameState> store = new ConcurrentHashMap<>();

    @Override
    public Optional<GameState> findById(String roomId) {
        return Optional.ofNullable(store.get(roomId));
    }

    @Override
    public void save(GameState state) {
        store.put(state.getRoomId(), state);
    }

    @Override
    public void deleteById(String roomId) {
        store.remove(roomId);
    }
}