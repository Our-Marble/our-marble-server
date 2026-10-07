package com.sparta.ourmarbleserver.property.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.game.service.GameDataService;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.PlayerState;
import com.sparta.ourmarbleserver.game.state.PropertyState;
import com.sparta.ourmarbleserver.property.domain.BuildingLevel;
import com.sparta.ourmarbleserver.property.service.PropertyService;

import tools.jackson.databind.json.JsonMapper;

class PropertyServiceTest {

    private PropertyService service;

    @BeforeEach
    void setUp() {
        service = new PropertyService(new GameDataService(JsonMapper.builder().build()));
    }

    private PropertyState property(int id, Long ownerId, BuildingLevel level) {
        PropertyState p = new PropertyState(id);
        p.setOwnerId(ownerId);
        p.setBuildingLevel(level);
        return p;
    }

    @Test
    void 방콕_호텔_금액() {
        PropertyState p = property(101, 1L, BuildingLevel.HOTEL);
        assertThat(service.getInvestedAmount(p)).isEqualTo(350000);
        assertThat(service.getSellValue(p)).isEqualTo(175000);
        assertThat(service.getAcquireValue(p)).isEqualTo(700000);
        assertThat(service.getToll(p)).isEqualTo(90000);
    }

    @Test
    void 땅만_있을_때와_별장() {
        assertThat(service.getInvestedAmount(property(101, 1L, BuildingLevel.LAND))).isEqualTo(50000);
        assertThat(service.getInvestedAmount(property(101, 1L, BuildingLevel.VILLA))).isEqualTo(100000);
        assertThat(service.getToll(property(101, 1L, BuildingLevel.LAND))).isEqualTo(2000);
    }

    @Test
    void 주인_없으면_통행료_0() {
        assertThat(service.getToll(property(101, null, BuildingLevel.LAND))).isZero();
    }

    @Test
    void 건설비는_한_단계_비용이고_건설_불가_땅은_0() {
        assertThat(service.getBuildCost(101, BuildingLevel.VILLA)).isEqualTo(50000);
        assertThat(service.getBuildCost(101, BuildingLevel.HOTEL)).isEqualTo(150000);
        assertThat(service.getBuildCost(101, BuildingLevel.LAND)).isZero();
        assertThat(service.getBuildCost(104, BuildingLevel.VILLA)).isZero();
    }

    @Test
    void 없는_땅은_0() {
        assertThat(service.getLandPrice(999)).isZero();
        assertThat(service.getInvestedAmount(property(999, 1L, BuildingLevel.LAND))).isZero();
    }

    @Test
    void 총자산과_전체_매각가() {
        GameState state = new GameState("r1");
        PlayerState player = new PlayerState(1L);
        player.setMoney(100000);
        state.addPlayer(player);
        state.addProperty(property(101, 1L, BuildingLevel.HOTEL));  // 투자금 350,000
        state.addProperty(property(103, 1L, BuildingLevel.LAND));   // 투자금 80,000
        state.addProperty(property(105, 2L, BuildingLevel.LAND));   // 남의 땅

        assertThat(service.getTotalAsset(state, 1L)).isEqualTo(530000);
        assertThat(service.getTotalSellValue(state, 1L)).isEqualTo(215000);
    }
}