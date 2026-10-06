package com.sparta.ourmarbleserver.game.state;

import com.sparta.ourmarbleserver.property.domain.BuildingLevel;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/** 땅 한 곳의 게임 중 상태. 가격표(고정 데이터)와는 별개로 주인과 건물 단계만 가진다. */
@Getter
@Setter
@RequiredArgsConstructor
public class PropertyState {

    private final int propertyId;

    /** 주인. 없으면 null. Long 비교는 ==가 아니라 isOwnedBy를 쓴다. */
    private Long ownerId;

    private BuildingLevel buildingLevel = BuildingLevel.LAND;

    public boolean hasOwner() {
        return ownerId != null;
    }

    public boolean isOwnedBy(long playerId) {
        return ownerId != null && ownerId == playerId;
    }

    /** 주인 없음 + 땅 단계로 되돌린다. (매각, 파산 시) */
    public void reset() {
        this.ownerId = null;
        this.buildingLevel = BuildingLevel.LAND;
    }
}