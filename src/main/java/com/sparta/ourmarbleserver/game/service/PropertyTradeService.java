package com.sparta.ourmarbleserver.game.service;


import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.dto.TileData;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.PlayerState;
import com.sparta.ourmarbleserver.game.state.PropertyState;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.GameErrorCode;
import com.sparta.ourmarbleserver.property.domain.BuildingLevel;
import com.sparta.ourmarbleserver.property.service.PropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * 땅 거래 규칙: 구매, 건설, 인수, 매각. 규칙에 어긋나면 GameException을 던지고, 던지기 전에는 상태를 바꾸지 않는다.
 * 공통 검증(내 차례, phase), 턴 종료, 저장, 알림, 요청 락은 GameService가 한다.
 * 같은 패키지에서만 보이게 해서 GameService를 거치지 않고는 부를 수 없다. (요청 락을 우회하지 못하게)
 */
@Service
@RequiredArgsConstructor
class PropertyTradeService {
    private final EconomyService economyService;
    private final PropertyService propertyService;
    private final GameDataService gameDataService;

    /**
     * 도착한 빈 땅을 산다. 거절(isAccept=false)이면 상태를 바꾸지 않는다.
     * 구매: 내 말이 그 땅 위(INVALID_PROPERTY) → 빈 땅(ALREADY_OWNED) → 현금 ≥ 땅값(NOT_ENOUGH_MONEY) → 땅값 차감, 주인 등록.
     */
    void purchase(GameState state, PlayerState player, int propertyId, boolean isAccept) {
        requireStandingOn(player, propertyId);
        if (!isAccept) {
            return;
        }

        PropertyState property = state.getPropertyState(propertyId).orElseThrow();
        if (property.hasOwner()) {
            throw new GameException(GameErrorCode.ALREADY_OWNED);
        }
        long price = propertyService.getLandPrice(propertyId);
        if (player.getMoney() < price) {
            throw new GameException(GameErrorCode.NOT_ENOUGH_MONEY);
        }

        economyService.charge(player, price);
        property.setOwnerId(player.getPlayerId());
    }

    /**
     * 내 땅에 한 단계 건설한다. 거절(isAccept=false)이면 상태를 바꾸지 않는다.
     * 건설 검증: 내 땅(NOT_OWNER) → 건설 가능 땅(CANNOT_BUILD) → 호텔 아님(MAX_LEVEL) → 현금 ≥ 건설비(NOT_ENOUGH_MONEY).
     * 건물은 한 단계씩 올라가고 건설비는 올라갈 단계의 비용이다.
     */
    void build(GameState state, PlayerState player, int propertyId, boolean isAccept) {
        requireStandingOn(player, propertyId);
        if (!isAccept) {
            return;
        }

        PropertyState property = state.getPropertyState(propertyId).orElseThrow();
        if (!property.isOwnedBy(player.getPlayerId())) {
            throw new GameException(GameErrorCode.NOT_OWNER);
        }
        if (!propertyService.canBuild(propertyId)) {
            throw new GameException(GameErrorCode.CANNOT_BUILD);
        }
        if (property.getBuildingLevel() == BuildingLevel.HOTEL) {
            throw new GameException(GameErrorCode.MAX_LEVEL);
        }
        BuildingLevel nextLevel = BuildingLevel.fromIndex(property.getBuildingLevel().index() + 1);
        long cost = propertyService.getBuildCost(propertyId, nextLevel);
        if (player.getMoney() < cost) {
            throw new GameException(GameErrorCode.NOT_ENOUGH_MONEY);
        }

        economyService.charge(player, cost);
        property.setBuildingLevel(nextLevel);
    }

    /**
     * 통행료를 낸 뒤 남의 땅을 인수한다. 거절(isAccept=false)이면 상태를 바꾸지 않는다.
     * 인수 검증: 남의 땅(CANNOT_ACQUIRE) → 현금 ≥ 인수가(NOT_ENOUGH_MONEY). 인수가는 이전 주인에게 이체하고
     * 건물 단계는 그대로 둔 채 주인만 바꾼다.
     */
    void acquire(GameState state, PlayerState player, int propertyId, boolean isAccept) {
        requireStandingOn(player, propertyId);
        if (!isAccept) {
            return;
        }

        PropertyState property = state.getPropertyState(propertyId).orElseThrow();
        if (!property.hasOwner() || property.isOwnedBy(player.getPlayerId())) {
            throw new GameException(GameErrorCode.CANNOT_ACQUIRE);
        }
        long price = propertyService.getAcquireValue(property);
        if (player.getMoney() < price) {
            throw new GameException(GameErrorCode.NOT_ENOUGH_MONEY);
        }

        economyService.transfer(player, state.getPlayerState(property.getOwnerId()), price);
        property.setOwnerId(player.getPlayerId());
    }

    /**
     * 통행료가 모자랄 때 땅을 팔아 현금을 채우고 통행료를 낸다.
     * 검증 순서: 목록이 null이거나 비었거나 null 값이 있거나 중복(INVALID_PROPERTY_LIST) → 없는 땅(INVALID_PROPERTY) → 내 땅 아님(NOT_OWNER)
     * → 현금 + 매각가 합계 < 통행료(NOT_ENOUGH_SELL). 땅은 주인 없음 + 건설 단계 0으로 초기화된다.
     */
    void sell(GameState state, PlayerState payer, List<Integer> propertyIds) {
        if (propertyIds == null || propertyIds.isEmpty()
                || propertyIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(propertyIds).size() != propertyIds.size()) {
            throw new GameException(GameErrorCode.INVALID_PROPERTY_LIST);
        }
        List<PropertyState> selected = new ArrayList<>();
        for (int propertyId : propertyIds) {
            PropertyState property = state.getPropertyState(propertyId)
                    .orElseThrow(() -> new GameException(GameErrorCode.INVALID_PROPERTY));
            if (!property.isOwnedBy(payer.getPlayerId())) {
                throw new GameException(GameErrorCode.NOT_OWNER);
            }
            selected.add(property);
        }

        PropertyState tollProperty = findTollProperty(state, payer);
        long toll = propertyService.getToll(tollProperty);
        long proceeds = selected.stream().mapToLong(propertyService::getSellValue).sum();
        if (payer.getMoney() + proceeds < toll) {
            throw new GameException(GameErrorCode.NOT_ENOUGH_SELL);
        }

        // 검증이 모두 끝났으니 상태를 바꾼다
        selected.forEach(PropertyState::reset);
        economyService.deposit(payer, proceeds);
        economyService.transfer(payer, state.getPlayerState(tollProperty.getOwnerId()), toll);
    }

    /**
     * 매각 대기 중인 플레이어가 통행료를 내야 하는 땅(내 말이 서 있는 남의 땅)을 찾는다. 아니면 상태 오류다.
     */
    private PropertyState findTollProperty(GameState state, PlayerState payer) {
        TileData tile = tileAt(payer.getPosition());
        if (!"PROPERTY".equals(tile.type()) || tile.propertyId() == null) {
            throw new GameException(GameErrorCode.INVALID_STATE);
        }
        PropertyState property = state.getPropertyState(tile.propertyId())
                .orElseThrow(() -> new GameException(GameErrorCode.INVALID_STATE));
        if (!property.hasOwner() || property.isOwnedBy(payer.getPlayerId())) {
            throw new GameException(GameErrorCode.INVALID_STATE);
        }
        return property;
    }

    /**
     * 내 말이 요청한 땅 위에 서 있는지 확인한다. 아니면 올바르지 않은 땅으로 거부한다.
     */
    private void requireStandingOn(PlayerState player, int propertyId) {
        TileData tile = tileAt(player.getPosition());
        if (!"PROPERTY".equals(tile.type()) || tile.propertyId() == null || tile.propertyId() != propertyId) {
            throw new GameException(GameErrorCode.INVALID_PROPERTY);
        }
    }

    private TileData tileAt(int position) {
        return gameDataService.getTiles().get(position);
    }
}