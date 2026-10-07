package com.sparta.ourmarbleserver.global.protocol;

/**
 * ERROR 알림: 요청이 거절된 이유. 요청한 사람에게만 보낸다.
 * <pre>
 * { "topic": "GAME", "type": "ERROR", "code": "NOT_YOUR_TURN", "message": "내 차례가 아닙니다." }
 * </pre>
 * topic은 요청의 topic을 그대로 쓰고, 해석할 수 없는 메시지였다면 LOBBY로 고정한다.
 *
 * @param code    ErrorCode 이름 (클라는 이 값으로 분기한다)
 * @param message 사람이 읽는 기본 문구
 */
public record ErrorPayload(String code, String message) {

    public static ErrorPayload of(ErrorCode errorCode) {
        return new ErrorPayload(errorCode.name(), errorCode.message());
    }
}
