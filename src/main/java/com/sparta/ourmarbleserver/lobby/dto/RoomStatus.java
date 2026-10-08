package com.sparta.ourmarbleserver.lobby.dto;

/** 방 상태. 클라가 getRoom을 폴링해서 PLAYING으로 바뀌면 게임 씬으로 넘어간다. */
public enum RoomStatus {
    WAITING, // 참가자 모집 중
    PLAYING  // 게임 시작됨
}
