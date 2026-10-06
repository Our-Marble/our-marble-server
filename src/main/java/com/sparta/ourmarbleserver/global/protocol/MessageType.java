package com.sparta.ourmarbleserver.global.protocol;

/**
 * 메시지 종류. 요청과 알림 모두 이 이름을 type 필드에 싣는다.
 * 구역별로 나눠 두었으니, 다른 담당은 맨 아래에 추가한다.
 */
public enum MessageType {

    // ===== GM: 클라 -> 서버 요청 (거절은 별도 요청 없이 isAccept 값으로 보낸다) =====
    BUILD,
    DRAW_CARD,
    CHOOSE_DESTINATION,
    ROLL_DICE,
    SELL_PROPERTIES,
    ACQUIRE_PROPERTY,
    PURCHASE_PROPERTY,

    // ===== GM: 서버 -> 클라 결과 알림 =====
    BUILT,
    CARD_DRAWN,
    DESTINATION_CHOSEN,
    DICE_ROLLED,
    PROPERTIES_SOLD,
    PROPERTY_ACQUIRED,
    PROPERTY_PURCHASED,

    // ===== 공통 =====
    ERROR

    // (게임 진행·로비·네트워크 담당 추가분은 아래에)
}