package com.sparta.ourmarbleserver.global.protocol;

/**
 * 요청 실패 시 ERROR 알림에 실리는 오류 코드와 기본 문구.
 * 오류가 나면 서버 상태는 바뀌지 않는다.
 */
public enum ErrorCode {

    // 기본
    NOT_YOUR_TURN("내 차례가 아닙니다."),
    INVALID_STATE("지금은 할 수 없는 요청입니다."),
    NOT_ENOUGH_MONEY("현금이 부족합니다."),
    INVALID_PROPERTY("올바르지 않은 땅입니다."),

    // 경제·부동산 추가
    ALREADY_OWNED("이미 주인이 있는 땅입니다."),
    NOT_OWNER("내 땅이 아닙니다."),
    CANNOT_BUILD("건설할 수 없는 땅입니다."),
    MAX_LEVEL("더 이상 건설할 수 없습니다."),
    CANNOT_ACQUIRE("인수할 수 없는 땅입니다."),
    NOT_ENOUGH_SELL("선택한 땅을 팔아도 통행료가 부족합니다."),
    PLAYER_BANKRUPT("파산한 플레이어입니다."),
    INVALID_PROPERTY_LIST("매각할 땅 목록이 올바르지 않습니다."),

    // 로비·네트워크
    INVALID_MESSAGE("메시지 형식이 올바르지 않습니다."),
    ROOM_NOT_FOUND("존재하지 않는 방입니다."),
    ROOM_FULL("방이 가득 찼습니다."),
    ALREADY_STARTED("이미 게임이 시작된 방입니다."),
    ALREADY_IN_ROOM("이미 방에 들어가 있습니다."),
    NOT_IN_ROOM("방에 들어가 있지 않습니다."),
    NOT_HOST("방장만 할 수 있습니다."),
    NOT_ENOUGH_PLAYERS("게임을 시작하려면 2명 이상이어야 합니다.");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}