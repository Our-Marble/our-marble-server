package com.sparta.ourmarbleserver.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record PlayRecordUpdateRequest(
        @NotNull
        @Schema(description = "이겼으면 true, 졌으면 false", example = "true")
        Boolean won
) {
}
