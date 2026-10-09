package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.event.GameEndedEvent;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.game.state.InMemoryGameStateRepository;
import com.sparta.ourmarbleserver.game.state.PlayerState;
import com.sparta.ourmarbleserver.game.state.PropertyState;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.property.domain.BuildingLevel;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import tools.jackson.databind.json.JsonMapper;

/**
 * 게임 종료 테스트: 최종 등수 부여와 종료 이벤트.
 * 등수 규칙(클라와 같음): 파산한 플레이어는 파산 시점의 생존자 수로 이미 정해져 있고,
 * 생존자는 총자산(현금 + 투자금) 내림차순 → 현금 내림차순 → 원래 턴 순서로 1위부터 부여한다.
 */
class GameServiceEndTest {

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
    private final List<Object> ended = new ArrayList<>();

    private GameService newService(int playerCount, int... faces) {
        GameDataService data = new GameDataService(JsonMapper.builder().build());
        GameService service = new GameService(repository, new DiceService(new FixedRandom(faces)),
                new MoveService(data), new TurnService(data), new EconomyService(), new PropertyService(data), data);
        service.setEventPublisher(ended::add);
        service.startGame(ROOM, LongStream.rangeClosed(1, playerCount).boxed().toList());
        return service;
    }

    private GameState state() {
        return repository.findById(ROOM).orElseThrow();
    }

    private PlayerState player(long playerId) {
        return state().getPlayerState(playerId);
    }

    private PropertyState property(int propertyId) {
        return state().getPropertyState(propertyId).orElseThrow();
    }

    /** 파산 상황: 플레이어 1이 주사위(1, 2)로 3번 칸(땅 103, 플레이어 2 소유, 호텔 통행료 180,000)에 도착한다. */
    private void prepareBankruptcy() {
        property(103).setOwnerId(2L);
        property(103).setBuildingLevel(BuildingLevel.HOTEL);
        property(101).setOwnerId(1L);
        player(1L).setMoney(10_000);
    }

    /** 마지막 라운드의 마지막 플레이어(2번)가 구매를 거절하고 턴을 끝내면 31라운드가 되어 게임이 끝나는 상태 */
    private void prepareRoundEnd(GameService service) {
        state().setCurrentPlayerId(2L);
        state().setRoundNumber(TurnService.MAX_ROUND);
        player(2L).setPosition(3);
        state().setPhase(TurnPhase.AWAITING_PURCHASE);
    }

    @Test
    void 두_명_중_한_명이_파산하면_생존자가_1위가_되고_종료_이벤트가_한_번_나간다() {
        GameService service = newService(2, 1, 2);
        prepareBankruptcy();

        service.rollDice( 1L);

        assertThat(state().isGameOver()).isTrue();
        assertThat(player(2L).getFinalRank()).isEqualTo(1);
        assertThat(player(1L).getFinalRank()).isEqualTo(2);
        assertThat(ended).containsExactly(new GameEndedEvent(ROOM));
    }

    @Test
    void 세_명_중_한_명이_파산해도_게임은_계속되고_생존자_등수는_정해지지_않는다() {
        GameService service = newService(3, 1, 2);
        prepareBankruptcy();

        service.rollDice( 1L);

        assertThat(state().isGameOver()).isFalse();
        assertThat(player(1L).getFinalRank()).isEqualTo(3);   // 파산자: 파산 시점의 생존자 수
        assertThat(player(2L).getFinalRank()).isZero();
        assertThat(player(3L).getFinalRank()).isZero();
        assertThat(ended).isEmpty();
    }

    @Test
    void 일반_진행_중에는_등수도_종료_이벤트도_없다() {
        GameService service = newService(2, 1, 2);

        service.rollDice( 1L);

        assertThat(state().isGameOver()).isFalse();
        assertThat(player(1L).getFinalRank()).isZero();
        assertThat(ended).isEmpty();
    }

    @Test
    void 최대_라운드가_끝나면_총자산이_많은_순서로_등수가_정해진다() {
        GameService service = newService(2);
        prepareRoundEnd(service);
        player(1L).setMoney(700_000);
        player(2L).setMoney(500_000);

        service.purchaseProperty( 2L, 103, false);

        assertThat(state().isGameOver()).isTrue();
        assertThat(player(1L).getFinalRank()).isEqualTo(1);
        assertThat(player(2L).getFinalRank()).isEqualTo(2);
        assertThat(ended).containsExactly(new GameEndedEvent(ROOM));
    }

    @Test
    void 총자산에는_가진_땅의_투자금이_포함된다() {
        GameService service = newService(2);
        prepareRoundEnd(service);
        property(101).setOwnerId(2L);   // 투자금 50,000
        player(1L).setMoney(520_000);   // 총자산 520,000
        player(2L).setMoney(500_000);   // 총자산 550,000

        service.purchaseProperty( 2L, 103, false);

        assertThat(player(2L).getFinalRank()).isEqualTo(1);
        assertThat(player(1L).getFinalRank()).isEqualTo(2);
    }

    @Test
    void 총자산이_같으면_현금이_많은_쪽이_앞선다() {
        GameService service = newService(2);
        prepareRoundEnd(service);
        property(101).setOwnerId(1L);   // 투자금 50,000
        player(1L).setMoney(500_000);   // 총자산 550,000, 현금 500,000
        player(2L).setMoney(550_000);   // 총자산 550,000, 현금 550,000

        service.purchaseProperty( 2L, 103, false);

        assertThat(player(2L).getFinalRank()).isEqualTo(1);
        assertThat(player(1L).getFinalRank()).isEqualTo(2);
    }

    @Test
    void 총자산과_현금이_모두_같으면_원래_턴_순서가_앞선_쪽이_앞선다() {
        GameService service = newService(2);
        prepareRoundEnd(service);

        service.purchaseProperty( 2L, 103, false);

        assertThat(player(1L).getFinalRank()).isEqualTo(1);
        assertThat(player(2L).getFinalRank()).isEqualTo(2);
    }

    @Test
    void 이미_파산한_플레이어의_등수는_바뀌지_않고_생존자만_1위부터_정해진다() {
        GameService service = newService(3);
        prepareRoundEnd(service);
        player(3L).setBankrupt(true);
        player(3L).setFinalRank(3);
        player(1L).setMoney(600_000);

        service.purchaseProperty( 2L, 103, false);

        assertThat(state().isGameOver()).isTrue();
        assertThat(player(1L).getFinalRank()).isEqualTo(1);
        assertThat(player(2L).getFinalRank()).isEqualTo(2);
        assertThat(player(3L).getFinalRank()).isEqualTo(3);
    }
}