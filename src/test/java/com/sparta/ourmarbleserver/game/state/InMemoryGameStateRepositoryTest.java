package com.sparta.ourmarbleserver.game.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 플레이어 번호로 진행 중인 게임 상태를 찾는 기능 테스트. */
class InMemoryGameStateRepositoryTest {

    private final InMemoryGameStateRepository repository = new InMemoryGameStateRepository();

    private GameState game(String roomId, long... playerIds) {
        GameState state = new GameState(roomId);
        for (long id : playerIds) {
            state.addPlayer(new PlayerState(id));
        }
        state.setPlayerOrder(java.util.Arrays.stream(playerIds).boxed().toList());
        repository.save(state);
        return state;
    }

    @Test
    void 플레이어_번호로_그_플레이어가_속한_게임을_찾는다() {
        game("r1", 1L, 2L);
        game("r2", 3L, 4L);

        assertThat(repository.findByPlayerId(1L)).get().extracting(GameState::getRoomId).isEqualTo("r1");
        assertThat(repository.findByPlayerId(4L)).get().extracting(GameState::getRoomId).isEqualTo("r2");
    }

    @Test
    void 어느_게임에도_없는_플레이어면_비어_있다() {
        game("r1", 1L, 2L);

        assertThat(repository.findByPlayerId(99L)).isEmpty();
    }

    @Test
    void 끝난_게임은_찾지_않는다() {
        GameState ended = game("r1", 1L, 2L);
        ended.setGameOver(true);

        assertThat(repository.findByPlayerId(1L)).isEmpty();
    }

    @Test
    void 같은_플레이어가_끝난_게임과_새_게임에_있으면_새_게임을_찾는다() {
        GameState ended = game("r1", 1L, 2L);
        ended.setGameOver(true);
        game("r2", 1L, 3L);

        assertThat(repository.findByPlayerId(1L)).get().extracting(GameState::getRoomId).isEqualTo("r2");
    }

    @Test
    void 삭제한_게임은_찾지_않는다() {
        game("r1", 1L, 2L);

        repository.deleteById("r1");

        assertThat(repository.findByPlayerId(1L)).isEmpty();
    }
}