package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.game.dto.GameMessage;
import com.sparta.ourmarbleserver.game.dto.GameStateSyncPayload;
import com.sparta.ourmarbleserver.game.dto.GameStateSyncPayload.GameSnapshot;
import com.sparta.ourmarbleserver.game.dto.GameStateSyncPayload.PropertySnapshot;
import com.sparta.ourmarbleserver.game.state.GameConfig;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.game.state.InMemoryGameStateRepository;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** GAME_STATE_SYNC(재접속 상태 복구) 알림 만들기 테스트. */
class GameStateSyncTest {

    private static final String ROOM = "r1";

    private final GameStateRepository repository = new InMemoryGameStateRepository();
    private final GameDataService data = new GameDataService(JsonMapper.builder().build());

    private GameService newService(int... faces) {
        return GameTestSupport.newGameService(repository, data, new FixedRandom(faces));
    }

    private GameSnapshot snapshotOf(GameService service, long playerId) {
        Optional<GameMessage> message = service.syncState(playerId);
        assertThat(message).isPresent();
        assertThat(message.get().type()).isEqualTo(MessageType.GAME_STATE_SYNC);
        return ((GameStateSyncPayload) message.get().payload()).gameState();
    }

    @Test
    void 시작한_직후의_상태가_담긴다() {
        GameService service = newService();
        service.startGame(ROOM, List.of(7L, 3L), Map.of(7L, "가"), Set.of());

        GameSnapshot snapshot = snapshotOf(service, 3L);

        assertThat(snapshot.roomId()).isEqualTo(ROOM);
        assertThat(snapshot.currentPlayerId()).isEqualTo(7L);
        assertThat(snapshot.phase()).isEqualTo("AWAITING_ROLL");
        assertThat(snapshot.playerOrder()).containsExactly(7L, 3L);
        assertThat(snapshot.playerStates()).hasSize(2);
        assertThat(snapshot.playerStates().getFirst().playerId()).isEqualTo(7L);
        assertThat(snapshot.playerStates().getFirst().nickname()).isEqualTo("가");
        assertThat(snapshot.playerStates().getFirst().money()).isEqualTo(GameService.START_MONEY);
        assertThat(snapshot.propertyStates()).isNotEmpty()
                .allMatch(property -> property.ownerId() == null && property.buildingLevel() == 0);
        assertThat(snapshot.config().startMoney()).isEqualTo(GameService.START_MONEY);
    }

    @Test
    void 진행된_결과가_상태에_반영된다() {
        GameService service = newService(1, 2);
        service.startGame(ROOM, List.of(1L, 2L));
        service.rollDice(1L);                         // 3번 칸(땅 103) 도착
        service.purchaseProperty(1L, 103, true);      // 땅을 사고 턴이 넘어감
        long price = new PropertyService(data).getLandPrice(103);

        GameSnapshot snapshot = snapshotOf(service, 2L);

        assertThat(snapshot.currentPlayerId()).isEqualTo(2L);
        assertThat(snapshot.phase()).isEqualTo("AWAITING_ROLL");
        assertThat(snapshot.playerStates().getFirst().position()).isEqualTo(3);
        assertThat(snapshot.playerStates().getFirst().money()).isEqualTo(GameService.START_MONEY - price);
        PropertySnapshot bought = snapshot.propertyStates().stream()
                .filter(property -> property.propertyId() == 103).findFirst().orElseThrow();
        assertThat(bought.ownerId()).isEqualTo(1L);
        assertThat(bought.buildingLevel()).isZero();
    }

    @Test
    void 속한_진행_중인_게임이_없으면_비어_있다() {
        GameService service = newService();

        assertThat(service.syncState(99L)).isEmpty();
    }

    @Test
    void 필드_이름이_클라이언트_상태_클래스에_맞는다() {
        GameService service = newService();
        service.startGame(ROOM, List.of(1L, 2L));
        JsonNode json = JsonMapper.builder().build().valueToTree(service.syncState(1L).orElseThrow().payload());

        JsonNode game = json.get("gameState");
        assertThat(game.has("roomId")).isTrue();
        assertThat(game.has("playerStates")).isTrue();
        assertThat(game.has("propertyStates")).isTrue();
        assertThat(game.has("isDouble")).isTrue();
        JsonNode player = game.get("playerStates").get(0);
        assertThat(player.has("isBankrupt")).isTrue();
        assertThat(player.has("bankrupt")).isFalse();
        assertThat(player.get("bot").asBoolean()).isFalse();   // 봇 여부는 재접속한 유저가 봇을 구분하도록 내려 준다
        assertThat(player.has("portraitId")).isFalse();        // 고르지 않았으면 빠진다
        assertThat(game.get("propertyStates").get(0).get("ownerId").isNull()).isTrue();
    }

    // ===== 초상화(portraitId)와 봇(bot) =====

    @Test
    void 플레이어마다_시작할_때_저장된_초상화와_봇_여부가_담긴다() {
        GameService service = newService();
        service.startGame(ROOM, List.of(1L, 2L, -1L), Map.of(), Set.of(-1L), Map.of(1L, 4), GameConfig.defaults());

        JsonNode players = JsonMapper.builder().build()
                .valueToTree(service.syncState(1L).orElseThrow().payload())
                .get("gameState").get("playerStates");

        assertThat(players.get(0).get("playerId").asLong()).isEqualTo(1L);
        assertThat(players.get(0).get("portraitId").asInt()).isEqualTo(4);
        assertThat(players.get(0).get("bot").asBoolean()).isFalse();
        assertThat(players.get(1).has("portraitId")).isFalse();   // 고르지 않은 사람은 빠진다
        assertThat(players.get(1).get("bot").asBoolean()).isFalse();
        assertThat(players.get(2).has("portraitId")).isFalse();   // 봇은 빠진다
        assertThat(players.get(2).get("bot").asBoolean()).isTrue();
    // ===== 방 정보(hostId, maxPlayers) =====

    private JsonNode syncJsonOf(GameService service, long playerId, boolean withRoom) {
        GameStateSyncPayload payload = (GameStateSyncPayload) service.syncState(playerId).orElseThrow().payload();
        if (withRoom) {
            payload = payload.withRoom(7L, 3);
        }
        return JsonMapper.builder().build().valueToTree(payload).get("gameState");
    }

    @Test
    void 방_정보를_붙이면_gameState_안에_hostId와_maxPlayers가_담긴다() {
        GameService service = newService();
        service.startGame(ROOM, List.of(7L, 3L), Map.of(7L, "가"), Set.of());

        JsonNode gameState = syncJsonOf(service, 3L, true);

        assertThat(gameState.get("hostId").asLong()).isEqualTo(7L);
        assertThat(gameState.get("maxPlayers").asInt()).isEqualTo(3);   // 지금 인원(2)이 아니라 방 설정값
        assertThat(gameState.get("playerStates")).hasSize(2);           // 기존 필드와 같은 위치
        assertThat(gameState.get("roomId").asText()).isEqualTo(ROOM);
    }

    @Test
    void 방_정보를_붙이지_않으면_두_필드가_JSON에서_빠진다() {
        GameService service = newService();
        service.startGame(ROOM, List.of(7L, 3L), Map.of(7L, "가"), Set.of());

        JsonNode gameState = syncJsonOf(service, 3L, false);

        assertThat(gameState.has("hostId")).isFalse();
        assertThat(gameState.has("maxPlayers")).isFalse();
        assertThat(gameState.has("phase")).isTrue();   // 나머지는 그대로
    }
}
