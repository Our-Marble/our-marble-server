package com.sparta.ourmarbleserver.property.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.sparta.ourmarbleserver.game.dto.PropertyData;
import com.sparta.ourmarbleserver.game.service.GameDataService;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.PropertyState;
import com.sparta.ourmarbleserver.property.domain.BuildingLevel;

/**
 * 가격표와 땅 상태로 금액을 계산한다. 상태를 바꾸지 않고 숫자만 돌려준다.
 * 모든 계산은 정수 연산이다. 가격표에 없는 땅은 0을 돌려준다.
 */
@Service
public class PropertyService {

    /** 매각가 = 투자금 * 50%, 인수가 = 투자금 * 200% */
    private static final long SELL_RATE_PERCENT = 50;
    private static final long ACQUIRE_RATE_PERCENT = 200;

    private final Map<Integer, PropertyData> prices = new HashMap<>();

    public PropertyService(GameDataService gameDataService) {
        for (PropertyData data : gameDataService.getProperties()) {
            prices.put(data.id(), data);
        }
    }

    public long getLandPrice(int propertyId) {
        PropertyData data = prices.get(propertyId);
        return data == null ? 0 : data.landPrice();
    }

    /** 건설할 수 있는 땅인지. 가격표에 없거나 건설 불가 땅(독도 등)이면 false */
    public boolean canBuild(int propertyId) {
        PropertyData data = prices.get(propertyId);
        return data != null && data.canBuild();
    }

    /** targetLevel까지 "한 단계" 짓는 비용 (누적 아님). 땅 단계이거나 건설 불가 땅이면 0 */
    public long getBuildCost(int propertyId, BuildingLevel targetLevel) {
        PropertyData data = prices.get(propertyId);
        if (data == null || !data.canBuild() || targetLevel == BuildingLevel.LAND) {
            return 0;
        }
        return data.buildCosts().get(targetLevel.index() - 1);
    }

    /** 현재 통행료. 주인이 없으면 0 */
    public long getToll(PropertyState property) {
        PropertyData data = prices.get(property.getPropertyId());
        if (data == null || !property.hasOwner()) {
            return 0;
        }
        return data.tolls().get(property.getBuildingLevel().index());
    }

    /** 투자금 = 땅값 + 지금 단계까지 지은 건물 비용 */
    public long getInvestedAmount(PropertyState property) {
        PropertyData data = prices.get(property.getPropertyId());
        if (data == null) {
            return 0;
        }
        long invested = data.landPrice();
        for (int i = 0; i < property.getBuildingLevel().index(); i++) {
            invested += data.buildCosts().get(i);
        }
        return invested;
    }

    public long getSellValue(PropertyState property) {
        return getInvestedAmount(property) * SELL_RATE_PERCENT / 100;
    }

    public long getAcquireValue(PropertyState property) {
        return getInvestedAmount(property) * ACQUIRE_RATE_PERCENT / 100;
    }

    /** 플레이어가 가진 땅 전체의 매각가 합계 */
    public long getTotalSellValue(GameState state, long playerId) {
        return state.properties().stream()
                .filter(p -> p.isOwnedBy(playerId))
                .mapToLong(this::getSellValue)
                .sum();
    }

    /** 총자산 = 현금 + 가진 땅의 투자금 합계 (게임 종료 등수 기준) */
    public long getTotalAsset(GameState state, long playerId) {
        long invested = state.properties().stream()
                .filter(p -> p.isOwnedBy(playerId))
                .mapToLong(this::getInvestedAmount)
                .sum();
        return state.getPlayerState(playerId).getMoney() + invested;
    }
}