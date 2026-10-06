package com.sparta.ourmarbleserver.game.state;

import java.util.Optional;

/**
 * 방별 게임 상태 저장소.
 * 지금은 메모리 구현이지만 3단계(Redis)에서 구현체만 바꿔 끼운다.
 * 메모리 구현은 같은 객체를 돌려주지만 Redis 구현은 복사본을 돌려주므로,
 * 서비스는 상태를 바꾼 뒤 항상 save()를 호출한다.
 */
public interface GameStateRepository {

    Optional<GameState> findById(String roomId);

    void save(GameState state);

    void deleteById(String roomId);
}