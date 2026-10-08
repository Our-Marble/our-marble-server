package com.sparta.ourmarbleserver.auth.dto;

import jakarta.validation.constraints.NotNull;

public record PlayRecordUpdateRequest(
        @NotNull Boolean won
) {
}
