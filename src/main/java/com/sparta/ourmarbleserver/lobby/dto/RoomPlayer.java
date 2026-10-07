package com.sparta.ourmarbleserver.lobby.dto;

/** 방에 참가한 플레이어. 방장은 항상 ready=true다. */
public record RoomPlayer(long playerId, boolean ready) {

}
