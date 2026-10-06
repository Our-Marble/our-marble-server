package com.sparta.ourmarbleserver.property.dto;

/** BUILT 알림: 건설 (거절이면 isAccept=false) */
public record BuiltPayload(long playerId, int propertyId, boolean isAccept) {
}