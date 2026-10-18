package com.sparta.ourmarbleserver.game.dto;

/** DESTINATION_CHOSEN 알림: 세계여행 목적지 선택 */
public record DestinationChosenPayload(long playerId, int destinationPosition) {
}