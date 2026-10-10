package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.state.GameConfig;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.game.state.InMemoryGameStateRepository;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import tools.jackson.databind.json.JsonMapper;

/** 방별 게임 설정(GameConfig)이 규칙에 적용되는지 확인한다. 설정을 안 넘기면 기본값으로 진행한다. */
class GameServiceConfigTest {

    private static final String ROOM = "r1";

    /** 정해 둔 주사위 눈(1~6)을 순서대로 돌려주는 Random */
    private static class FixedRandom extends Random {
        private final Queue<Integer> faces = new ArrayDeque<>();

        FixedRandom(int... values) {
            for (int v : values) {
                faces.add(v);
            }
        }

        @Override
        public int nextInt(int bound) {
            return faces.remove() - 1;
        }
    }

    private final GameStateRepository repository = new InMemoryGameStateRepository();

    private GameService newService(int... faces) {
        GameDataService data = new GameDataService(JsonMapper.builder().build());
        return new GameService(repository, new DiceService(new FixedRandom(faces)),
                new MoveService(data), new TurnService(data), new EconomyService(), new PropertyService(data), data);
    }

    private GameState state(String roomId) {
        return repository.findById(roomId).orElseThrow();
    }

    private GameState start(GameService service, GameConfig config) {
        return service.startGame(ROOM, List.of(1L, 2L), Map.of(), Set.of(), config);
    }

    @Test
    void 설정을_안_넘기면_기본_설정으로_시작한다() {
        GameService service = newService();

        GameState state = service.startGame(ROOM, List.of(1L, 2L));

        assertThat(state.getConfig()).isEqualTo(GameConfig.defaults());
        assertThat(state.getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY);
    }

    @Test
    void 초기_자금이_설정을_따른다() {
        GameService service = newService();

        GameState state = start(service, new GameConfig(1_000_000, 100_000, 100_000, 30, 3));

        assertThat(state.getPlayerState(1L).getMoney()).isEqualTo(1_000_000);
        assertThat(state.getPlayerState(2L).getMoney()).isEqualTo(1_000_000);
    }

    @Test
    void 월급이_설정_금액으로_지급된다() {
        GameService service = newService(1, 2);
        start(service, new GameConfig(500_000, 20_000, 100_000, 30, 3));
        state(ROOM).getPlayerState(1L).setPosition(30);   // 30 + 3 = 33 → 1번 칸, 출발 지점을 지난다

        service.rollDice(1L);

        assertThat(state(ROOM).getPlayerState(1L).getMoney()).isEqualTo(500_000 + 20_000);
    }

    @Test
    void 세금이_설정_금액으로_부과되고_적립금에_쌓인다() {
        GameService service = newService(1, 2);
        start(service, new GameConfig(500_000, 100_000, 50_000, 30, 3));
        state(ROOM).getPlayerState(1L).setPosition(27);   // 27 + 3 = 30 (세무조사)

        service.rollDice(1L);

        assertThat(state(ROOM).getPlayerState(1L).getMoney()).isEqualTo(500_000 - 50_000);
        assertThat(state(ROOM).getWelfareFund()).isEqualTo(50_000);
    }

    @Test
    void 최대_라운드가_설정을_따른다() {
        GameService service = newService(1, 2, 1, 2);
        start(service, new GameConfig(500_000, 100_000, 100_000, 1, 3));   // 1라운드까지만

        service.rollDice(1L);                      // 플레이어 1: 3번 칸 도착
        service.purchaseProperty(1L, 103, false);  // 거절하고 턴 종료 → 플레이어 2 (아직 1라운드)
        assertThat(state(ROOM).isGameOver()).isFalse();

        service.rollDice(2L);                      // 플레이어 2: 3번 칸 도착
        service.purchaseProperty(2L, 103, false);  // 거절하고 턴 종료 → 2라운드가 되어야 하므로 게임 종료

        assertThat(state(ROOM).isGameOver()).isTrue();
    }

    @Test
    void 무인도_영업정지_턴이_설정을_따른다() {
        GameService service = newService(1, 2);
        start(service, new GameConfig(500_000, 100_000, 100_000, 30, 1));
        state(ROOM).getPlayerState(1L).setPosition(5);   // 5 + 3 = 8 (무인도)

        service.rollDice(1L);

        assertThat(state(ROOM).getPlayerState(1L).getIslandTurnsRemaining()).isEqualTo(1);
    }

    @Test
    void 방마다_다른_설정으로_진행할_수_있다() {
        GameService service = newService();

        GameState first = service.startGame("r1", List.of(1L, 2L), Map.of(), Set.of(),
                new GameConfig(1_000_000, 100_000, 100_000, 30, 3));
        GameState second = service.startGame("r2", List.of(3L, 4L));

        assertThat(first.getPlayerState(1L).getMoney()).isEqualTo(1_000_000);
        assertThat(second.getPlayerState(3L).getMoney()).isEqualTo(GameService.START_MONEY);
        assertThat(first.getConfig()).isNotEqualTo(second.getConfig());
    }
}