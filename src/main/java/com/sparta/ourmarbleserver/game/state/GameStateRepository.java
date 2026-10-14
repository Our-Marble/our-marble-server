package com.sparta.ourmarbleserver.game.state;

import java.util.Optional;

/**
 * 방별 게임 상태 저장소.
 * 서버는 RedisGameStateRepository, 테스트는 InMemoryGameStateRepository를 쓴다.
 * 메모리 구현은 같은 객체를 돌려주지만 Redis 구현은 복사본을 돌려주므로,
 * 서비스는 상태를 바꾼 뒤 항상 save()를 호출한다.
 */
public interface GameStateRepository {

    Optional<GameState> findById(String roomId);

    void save(GameState state);

    void deleteById(String roomId);

    /** 플레이어가 속한 진행 중인(끝나지 않은) 게임 상태를 찾는다. 그 게임에서 파산한 플레이어는 속하지 않은 것으로 본다. */
    Optional<GameState> findByPlayerId(long playerId);
}