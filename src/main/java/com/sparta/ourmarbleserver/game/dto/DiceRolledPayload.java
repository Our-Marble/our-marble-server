package com.sparta.ourmarbleserver.game.dto;

/** ROLL_DICE 알림: 주사위 결과 */
public record DiceRolledPayload(long playerId, int dice1, int dice2) {
}