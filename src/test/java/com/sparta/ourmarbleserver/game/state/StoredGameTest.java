package com.sparta.ourmarbleserver.game.state;

import com.sparta.ourmarbleserver.property.domain.BuildingLevel;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoredGameTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void JSON으로_저장했다가_꺼내도_게임_상태가_같다() {
        GameState original = new GameState("r_1");
        original.setTurnNumber(12);
        original.setRoundNumber(3);
        original.setCurrentPlayerId(2L);
        original.setWelfareFund(100_000);
        original.setPhase(TurnPhase.AWAITING_PURCHASE);
        original.setDouble(true);
        original.setConsecutiveDoubleCount(1);
        original.setConfig(GameConfig.defaults().withLimitTimeSeconds(7));
        original.setPlayerOrder(List.of(2L, 1L));   // 턴 순서

        PlayerState p1 = new PlayerState(1L);
        p1.setPosition(8);
        p1.setMoney(420_000);
        p1.setIslandTurnsRemaining(2);
        p1.setBankrupt(true);
        p1.setNickname("철수");
        p1.setFinalRank(2);
        p1.setPortraitId(6);   // 고른 초상화. p2(봇)는 없음(null)
        original.addPlayer(p1);

        PlayerState p2 = new PlayerState(2L);
        p2.setMoney(310_000);
        p2.setBot(true);
        p2.setNickname("봇");
        original.addPlayer(p2);

        PropertyState owned = new PropertyState(104);
        owned.setOwnerId(2L);
        owned.setBuildingLevel(BuildingLevel.BUILDING);
        original.addProperty(owned);
        original.addProperty(new PropertyState(105));   // 주인 없음 (ownerId=null)

        String json = objectMapper.writeValueAsString(StoredGame.from(original));
        GameState restored = objectMapper.readValue(json, StoredGame.class).toState();

        assertThat(restored).usingRecursiveComparison().isEqualTo(original);
        assertThat(restored.playerOrder()).containsExactly(2L, 1L);
    }
}