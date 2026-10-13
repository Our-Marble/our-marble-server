package com.sparta.ourmarbleserver.global.protocol;

public interface ErrorCode {

    // 오류 코드 이름, enum에는 자동으로 있음
    String name();

    // 메시지
    String message();

    // 이 오류를 보낼 때 쓰는 메시지 type (COMMON_ERROR / LOBBY_ERROR / GAME_ERROR)
    MessageType type();
}
