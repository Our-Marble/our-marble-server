package com.sparta.ourmarbleserver.global.protocol;

public enum LobbyErrorCode implements ErrorCode {
    // 로비
    INVALID_STATE("지금은 할 수 없는 요청입니다."),
    ROOM_NOT_FOUND("방을 찾을 수 없습니다."),
    ROOM_FULL("방이 가득 찼습니다."),
    ALREADY_IN_ROOM("이미 방에 참가 중입니다."),
    NOT_IN_ROOM("방에 참가하지 않았습니다."),
    NOT_HOST("방장만 할 수 있습니다."),
    ROOM_ALREADY_STARTED("이미 시작한 방입니다."),
    NOT_ENOUGH_PLAYERS("최소 2명이 필요합니다."),
    NOT_ALL_READY("모두 준비해야 시작할 수 있습니다."),
    INVALID_MAX_PLAYERS("최대 인원은 2~4명이고, 지금 방 인원보다 적을 수 없습니다.");

    private final String message;

    LobbyErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public MessageType type() {
        return MessageType.LOBBY_ERROR;
    }
}
