package com.sparta.ourmarbleserver.game.dto;

/** cards.json의 카드 하나. effectType은 Bonus, Penalty, MoveTo, MoveBy, GoToInspection */
public record CardData(int id, String name, String description, String effectType,
                       long amount, int targetTileId, int steps, boolean penaltyToFestivalPool) {
}