package com.sparta.ourmarbleserver.auth.dto;

import com.sparta.ourmarbleserver.auth.entity.Role;

public record AuthPlayer(
        long playerId,
        Role role
) {
}
