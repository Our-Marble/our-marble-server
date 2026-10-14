package com.sparta.ourmarbleserver.global.protocol;

/**
 * 오류 알림의 본문: 요청이 거절된 이유. 요청한 사람에게만 보낸다.
 * type은 오류 코드가 속한 enum이 정한다 (ErrorCode.type()).
 * <pre>
 * { "type": "GAME_ERROR",   "code": "NOT_YOUR_TURN",   "message": "내 차례가 아닙니다." }
 * { "type": "LOBBY_ERROR",  "code": "ROOM_FULL",       "message": "방이 가득 찼습니다." }
 * { "type": "COMMON_ERROR", "code": "INVALID_MESSAGE", "message": "메시지 형식이 올바르지 않습니다." }
 * </pre>
 *
 * @param code    오류 코드 이름 (클라는 type으로 화면을 고르고, code로 세부 분기한다)
 * @param message 사람이 읽는 기본 문구
 */
public record ErrorPayload(String code, String message) {

    public static ErrorPayload of(ErrorCode errorCode) {
        return new ErrorPayload(errorCode.name(), errorCode.message());
    }
}
