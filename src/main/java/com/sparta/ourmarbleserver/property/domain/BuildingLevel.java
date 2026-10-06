package com.sparta.ourmarbleserver.property.domain;

/**
 * 부동산 칸의 건물 단계.
 * index는 properties.json의 단계별 배열 인덱스이자, 클라와 주고받는 buildingLevel 값이다.
 * (tolls: 땅·별장·빌딩·호텔 순서 / buildCosts: 별장·빌딩·호텔 순서)
 */
public enum BuildingLevel {
    LAND(0),      // 땅만 있음 (구매 직후)
    VILLA(1),     // 별장
    BUILDING(2),  // 빌딩
    HOTEL(3);     // 호텔

    private final int index;

    BuildingLevel(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    public static BuildingLevel fromIndex(int index) {
        for (BuildingLevel level : values()) {
            if (level.index == index) {
                return level;
            }
        }
        throw new IllegalArgumentException("존재하지 않는 건물 단계: " + index);
    }
}