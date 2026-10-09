package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.game.state.InMemoryGameStateRepository;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import tools.jackson.databind.json.JsonMapper;

/** 게임 시작(startGame) 테스트: 로비가 넘기는 플레이어 목록, 닉네임, 봇 정보. */
class GameServiceStartTest {

    private static final String ROOM = "r1";

    private final GameStateRepository repository = new InMemoryGameStateRepository();

    private GameService newService() {
        GameDataService data = new GameDataService(JsonMapper.builder().build());
        return new GameService(repository, new DiceService(),
                new MoveService(data), new TurnService(data), new EconomyService(), new PropertyService(data), data);
    }

    @Test
    void 닉네임과_봇_정보를_넘기면_플레이어_상태에_들어간다() {
        GameService service = newService();

        GameState state = service.startGame(ROOM, List.of(10L, 20L, 30L),
                Map.of(10L, "가", 20L, "나"), Set.of(30L));

        assertThat(state.getPlayerState(10L).getNickname()).isEqualTo("가");
        assertThat(state.getPlayerState(10L).isBot()).isFalse();
        assertThat(state.getPlayerState(20L).getNickname()).isEqualTo("나");
        assertThat(state.getPlayerState(30L).getNickname()).isEmpty();   // 목록에 없으면 빈 문자열
        assertThat(state.getPlayerState(30L).isBot()).isTrue();
    }

    @Test
    void 닉네임과_봇_정보를_안_넘기면_닉네임은_빈_문자열이고_봇이_아니다() {
        GameService service = newService();

        GameState state = service.startGame(ROOM, List.of(1L, 2L));

        assertThat(state.getPlayerState(1L).getNickname()).isEmpty();
        assertThat(state.getPlayerState(1L).isBot()).isFalse();
        assertThat(state.getPlayerState(2L).isBot()).isFalse();
    }

    @Test
    void 목록_순서가_턴_순서이고_첫_플레이어의_턴으로_시작한다() {
        GameService service = newService();

        GameState state = service.startGame(ROOM, List.of(7L, 3L));

        assertThat(state.playerOrder()).containsExactly(7L, 3L);
        assertThat(state.getCurrentPlayerId()).isEqualTo(7L);
        assertThat(state.getPhase()).isEqualTo(TurnPhase.AWAITING_ROLL);
        assertThat(state.getPlayerState(7L).getMoney()).isEqualTo(GameService.START_MONEY);
        assertThat(state.getPlayerState(3L).getMoney()).isEqualTo(GameService.START_MONEY);
    }

    @Test
    void 시작한_방_상태는_저장소에서_찾을_수_있다() {
        GameService service = newService();

        service.startGame(ROOM, List.of(1L, 2L));

        assertThat(repository.findById(ROOM)).isPresent();
    }

    @Test
    void 플레이어_목록이_비었거나_중복이면_거부된다() {
        GameService service = newService();

        assertThatThrownBy(() -> service.startGame(ROOM, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.startGame(ROOM, List.of(1L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findById(ROOM)).isEmpty();
    }
}