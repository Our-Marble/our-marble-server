package com.sparta.ourmarbleserver.game.dto;

import java.util.List;

/** properties.json의 땅 하나. buildCosts: 별장·빌딩·호텔 / tolls: 땅·별장·빌딩·호텔 */
public record PropertyData(int id, int boardIndex, String cityName, boolean canBuild,
                           long landPrice, List<Long> buildCosts, List<Long> tolls) {
}