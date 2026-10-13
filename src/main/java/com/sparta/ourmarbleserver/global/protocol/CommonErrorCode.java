package com.sparta.ourmarbleserver.global.protocol;

public enum CommonErrorCode implements ErrorCode {
    INVALID_MESSAGE("메시지 형식이 올바르지 않습니다."); // 네트워크: JSON이 아니거나 type이 없거나 모르는 값

    private final String message;

    CommonErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public MessageType type() {
        return MessageType.COMMON_ERROR;
    }
}
