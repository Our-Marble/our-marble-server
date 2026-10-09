package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.game.state.InMemoryGameStateRepository;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.transport.FakeEventPublisher;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import tools.jackson.databind.json.JsonMapper;

/**
 * 같은 요청이 동시에 여러 번 와도 한 번만 처리되는지 확인한다.
 * 잠금이 없으면 여러 요청이 검증을 동시에 통과해서 땅값이 여러 번 빠질 수 있다.
 */
class GameServiceConcurrencyTest {

    private static final String ROOM = "r1";
    private static final int PROPERTY_ID = 103;
    private static final int THREADS = 16;

    private final GameStateRepository repository = new InMemoryGameStateRepository();
    private PropertyService propertyService;

    private GameService newService() {
        GameDataService data = new GameDataService(JsonMapper.builder().build());
        propertyService = new PropertyService(data);
        GameService service = new GameService(repository, new DiceService(),
                new MoveService(data), new TurnService(data), new EconomyService(), propertyService, data);
        service.startGame(ROOM, List.of(1L, 2L));

        GameState state = repository.findById(ROOM).orElseThrow();
        state.getPlayerState(1L).setPosition(3);
        state.setPhase(TurnPhase.AWAITING_PURCHASE);
        return service;
    }

    @Test
    void 구매_요청이_동시에_와도_한_번만_처리되고_땅값은_한_번만_빠진다() throws Exception {
        GameService service = newService();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    service.purchaseProperty( 1L, PROPERTY_ID, true);
                    succeeded.incrementAndGet();
                } catch (GameException e) {
                    rejected.incrementAndGet();   // 이미 구매가 끝나 턴이 넘어간 뒤의 요청
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        GameState state = repository.findById(ROOM).orElseThrow();
        long price = propertyService.getLandPrice(PROPERTY_ID);
        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(THREADS - 1);
        assertThat(state.getPlayerState(1L).getMoney()).isEqualTo(GameService.START_MONEY - price);
        assertThat(state.getPropertyState(PROPERTY_ID).orElseThrow().isOwnedBy(1L)).isTrue();
        assertThat(state.getCurrentPlayerId()).isEqualTo(2L);
    }
}