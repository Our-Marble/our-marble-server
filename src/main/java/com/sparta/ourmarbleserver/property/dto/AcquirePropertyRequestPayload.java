package com.sparta.ourmarbleserver.property.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** ACQUIRE_PROPERTY 요청: 통행료를 낸 뒤 남의 땅을 인수하거나 거절한다. */
public record AcquirePropertyRequestPayload(
        @Schema(description = "땅 번호", example = "104") int propertyId,
        @Schema(description = "true면 인수, false면 거절", example = "true") boolean isAccept) {
}