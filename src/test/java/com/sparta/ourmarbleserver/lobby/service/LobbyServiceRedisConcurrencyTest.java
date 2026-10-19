package com.sparta.ourmarbleserver.lobby.service;

import com.sparta.ourmarbleserver.auth.dto.PlayerResponse;
import com.sparta.ourmarbleserver.auth.service.PlayerService;
import com.sparta.ourmarbleserver.bot.service.BotService;
import com.sparta.ourmarbleserver.game.service.GameService;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.LobbyErrorCode;
import com.sparta.ourmarbleserver.lobby.dto.RoomInfo;
import com.sparta.ourmarbleserver.lobby.state.RedisRoomLock;
import com.sparta.ourmarbleserver.lobby.state.RedisRoomRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * 실제 Redis로 로비 동시성을 확인한다. (LobbyService에서 전역 synchronized를 뺀 뒤의 안전성 검증)
 * 서비스 두 개(server A, B)가 같은 Redis를 쓰게 해서 "같은 서버의 여러 스레드"와 "서로 다른 서버"를 함께 흉내 낸다.
 *
 * 로컬 Redis(compose.yaml의 redis, localhost:${REDIS_PORT:6379})의 15번 DB를 비우고 쓴다.
 * Redis에 접속할 수 없으면 테스트를 건너뛴다. (docker compose up -d redis 후 실행)
 */
class LobbyServiceRedisConcurrencyTest {

    private static final int TEST_DB = 15;
    private static final long HOST = 1L;
    private static final int MAP = 1;

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private LobbyService serverA;
    private LobbyService serverB;

    /** DB 없이 닉네임을 돌려주는 PlayerService */
    private static class FakePlayerService extends PlayerService {
        FakePlayerService() {
            super(null, null);
        }

        @Override
        public PlayerResponse findMe(long userId) {
            return new PlayerResponse(userId, "", "닉" + userId);
        }
    }

    @BeforeEach
    void setUp() {
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", port);
        config.setDatabase(TEST_DB);
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .build();
        factory = new LettuceConnectionFactory(config, client);
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);

        boolean available;
        try {
            flushTestDb();
            available = true;
        } catch (RuntimeException e) {
            available = false;
        }
        assumeTrue(available, "로컬 Redis(localhost:" + port + ")에 접속할 수 없어 건너뜀");

        serverA = newServer();
        serverB = newServer();
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            try {
                flushTestDb();
            } catch (RuntimeException ignored) {
                // Redis가 없어서 건너뛴 경우
            }
            factory.destroy();
        }
    }

    private void flushTestDb() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    private LobbyService newServer() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        return new LobbyService(
                new RedisRoomRepository(redis, objectMapper),
                mock(GameService.class),
                new FakePlayerService(),
                new BotService(redis),
                new RedisRoomLock(redis));
    }

    /** 여러 작업을 동시에 출발시키고 끝날 때까지 기다린다. 각 작업의 예외는 결과 목록에 담는다. */
    private static List<Throwable> runConcurrently(List<Runnable> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        for (Runnable task : tasks) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    task.run();
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        return errors;
    }

    @Test
    void 여러_명이_동시에_한_방에_입장해도_정원을_넘지_않는다() throws Exception {
        String roomId = serverA.createRoom(HOST, MAP, 4).roomId();   // 방장 포함 4명 정원 → 3자리 남음
        int joiners = 20;
        AtomicInteger succeeded = new AtomicInteger();
        List<Runnable> tasks = new ArrayList<>();
        for (long p = 2; p < 2 + joiners; p++) {
            long playerId = p;
            LobbyService server = (p % 2 == 0) ? serverA : serverB;   // 두 서버로 나눠서 요청
            tasks.add(() -> {
                server.join(roomId, playerId);
                succeeded.incrementAndGet();
            });
        }

        List<Throwable> errors = runConcurrently(tasks);

        RoomInfo room = serverA.getRoom(roomId);
        assertThat(room.players()).hasSize(4);
        assertThat(succeeded.get()).isEqualTo(3);
        assertThat(errors).hasSize(joiners - 3)
                .allSatisfy(e -> assertThat(e).isInstanceOfSatisfying(GameException.class,
                        ge -> assertThat(ge.code()).isEqualTo(LobbyErrorCode.ROOM_FULL)));

        // 장부(playerId → roomId)에도 방에 들어간 사람만 남는다
        Map<Object, Object> index = redis.opsForHash().entries("lobby:player-room");
        assertThat(index).hasSize(4).allSatisfy((player, room2) -> assertThat(room2).isEqualTo(roomId));
    }

    @Test
    void 모두_동시에_나가면_방이_삭제되고_장부도_비워진다() throws Exception {
        String roomId = serverA.createRoom(HOST, MAP, 4).roomId();
        serverA.join(roomId, 2L);
        serverB.join(roomId, 3L);
        serverB.join(roomId, 4L);

        List<Runnable> tasks = new ArrayList<>();
        for (long p = 1; p <= 4; p++) {
            long playerId = p;
            LobbyService server = (p % 2 == 0) ? serverA : serverB;
            tasks.add(() -> server.leave(roomId, playerId));
        }

        List<Throwable> errors = runConcurrently(tasks);

        assertThat(errors).isEmpty();
        assertThat(serverA.waitingRoomIds()).doesNotContain(roomId);
        assertThat(redis.hasKey("lobby:room:" + roomId)).isFalse();
        assertThat(redis.opsForHash().entries("lobby:player-room")).isEmpty();
    }

    @Test
    void 방장이_다른_사람과_동시에_나가도_남은_사람이_방장이_된다() throws Exception {
        String roomId = serverA.createRoom(HOST, MAP, 4).roomId();
        serverA.join(roomId, 2L);
        serverA.join(roomId, 3L);

        List<Throwable> errors = runConcurrently(List.of(
                () -> serverA.leave(roomId, HOST),
                () -> serverB.leave(roomId, 2L)));

        assertThat(errors).isEmpty();
        RoomInfo room = serverA.getRoom(roomId);
        assertThat(room.players()).hasSize(1);
        assertThat(room.hostId()).isEqualTo(3L);
        assertThat(room.players().getFirst().ready()).isTrue();   // 방장은 항상 준비 상태
    }

    /**
     * 전역 synchronized가 있을 때는 한 방의 락을 기다리는 요청이 서버 전체의 로비 요청을 막았다.
     * 이제는 다른 방의 요청과 방 조회가 그 기다림과 상관없이 바로 처리되어야 한다.
     */
    @Test
    void 한_방의_락을_기다리는_동안에도_다른_방의_요청은_바로_처리된다() throws Exception {
        String busyRoom = serverA.createRoom(HOST, MAP, 4).roomId();
        String otherRoom = serverA.createRoom(10L, MAP, 4).roomId();
        RedisRoomLock otherServerLock = new RedisRoomLock(redis);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch held = new CountDownLatch(1);
        // 다른 서버가 busyRoom의 락을 1.5초 동안 잡고 있다
        Future<?> holder = pool.submit(() -> otherServerLock.call(busyRoom, () -> {
            held.countDown();
            sleep(1_500);
            return null;
        }));
        held.await();
        // 이 서버의 한 스레드는 busyRoom 입장을 위해 락을 기다린다
        Future<?> waiter = pool.submit(() -> serverA.join(busyRoom, 2L));
        Thread.sleep(100);

        long start = System.nanoTime();
        serverA.join(otherRoom, 11L);                 // 다른 방 입장
        serverA.findRoomByPlayer(10L);                // 방 조회 (채팅 경로)
        serverA.roomList();                           // 방 목록
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMillis).isLessThan(500);
        holder.get(5, TimeUnit.SECONDS);
        waiter.get(5, TimeUnit.SECONDS);              // 락이 풀리면 기다리던 입장도 성공한다
        assertThat(serverA.getRoom(busyRoom).players()).hasSize(2);
        pool.shutdown();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
