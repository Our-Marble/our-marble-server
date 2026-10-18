package com.sparta.ourmarbleserver.property.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** SELL_PROPERTIES 요청: 통행료가 모자랄 때 팔 땅의 목록. 비어 있거나 중복이 있으면 거부된다. */
public record SellPropertiesRequestPayload(
        @Schema(description = "팔 땅 번호 목록", example = "[104, 105]") List<Integer> propertyIds) {
}