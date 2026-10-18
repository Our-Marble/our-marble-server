package com.sparta.ourmarbleserver.property.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** BUILD 요청: 내 땅에 한 단계 건설하거나 거절한다. */
public record BuildRequestPayload(
        @Schema(description = "땅 번호", example = "104") int propertyId,
        @Schema(description = "true면 건설, false면 거절", example = "true") boolean isAccept) {
}