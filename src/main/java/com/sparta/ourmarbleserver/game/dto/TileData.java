package com.sparta.ourmarbleserver.game.dto;

/** board.json의 칸 하나. propertyId는 PROPERTY 칸에만 있다. */
public record TileData(int index, String type, Integer propertyId) {
}