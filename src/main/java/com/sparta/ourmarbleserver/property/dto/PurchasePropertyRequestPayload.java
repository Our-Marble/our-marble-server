package com.sparta.ourmarbleserver.property.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** PURCHASE_PROPERTY 요청: 도착한 빈 땅을 사거나 거절한다. */
public record PurchasePropertyRequestPayload(
        @Schema(description = "땅 번호", example = "104") int propertyId,
        @Schema(description = "true면 구매, false면 거절", example = "true") boolean isAccept) {
}