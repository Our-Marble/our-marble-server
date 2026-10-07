package com.sparta.ourmarbleserver.lobby.domain;

/** 방 상태. 게임 중인 방도 로비 목록에 보이며, 입장만 막는다. */
public enum RoomStatus {
    WAITING, // 대기 중 (입장 가능)
    PLAYING  // 게임 중 (입장 불가)
}
