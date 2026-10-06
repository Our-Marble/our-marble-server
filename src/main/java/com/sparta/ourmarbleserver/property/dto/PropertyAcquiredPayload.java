package com.sparta.ourmarbleserver.property.dto;

/** PROPERTY_ACQUIRED 알림: 인수 (거절이면 isAccept=false) */
public record PropertyAcquiredPayload(long playerId, int propertyId, boolean isAccept) {
}