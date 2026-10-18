package com.sparta.ourmarbleserver.global.exception;

import com.sparta.ourmarbleserver.global.protocol.ErrorCode;

/**
 * 요청 검증에 실패했을 때 서비스가 던지는 예외.
 * 전송 계층이 잡아서 요청자에게만 ERROR 알림으로 바꿔 보낸다.
 */
public class GameException extends RuntimeException {

    private final ErrorCode code;

    public GameException(ErrorCode code) {
        super(code.message());
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}