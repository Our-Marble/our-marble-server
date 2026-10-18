package com.sparta.ourmarbleserver.property.dto;

/** PROPERTY_PURCHASED 알림: 땅 구매 (거절이면 isAccept=false) */
public record PropertyPurchasedPayload(long playerId, int propertyId, boolean isAccept) {
}