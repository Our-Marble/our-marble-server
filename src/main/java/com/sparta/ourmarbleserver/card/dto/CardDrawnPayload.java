package com.sparta.ourmarbleserver.card.dto;

/** CARD_DRAWN 알림: 황금열쇠 카드 뽑기 */
public record CardDrawnPayload(long playerId, int cardId) {
}