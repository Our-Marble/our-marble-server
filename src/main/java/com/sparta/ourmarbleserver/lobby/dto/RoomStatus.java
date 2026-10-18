package com.sparta.ourmarbleserver.lobby.dto;

/** 방 상태. 같은 방이 대기(WAITING) → 게임 중(PLAYING) → 게임이 끝나면 다시 대기로 바뀐다. */
public enum RoomStatus {
    WAITING, // 참가자 모집 중
    PLAYING  // 게임 시작됨
}
