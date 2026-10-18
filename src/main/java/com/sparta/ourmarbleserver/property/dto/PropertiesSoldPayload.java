package com.sparta.ourmarbleserver.property.dto;

import java.util.List;

/** PROPERTIES_SOLD 알림: 매각 */
public record PropertiesSoldPayload(long playerId, List<Integer> propertyIds) {
}