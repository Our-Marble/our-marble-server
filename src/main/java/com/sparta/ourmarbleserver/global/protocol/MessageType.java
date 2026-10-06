package com.sparta.ourmarbleserver.global.protocol;

/**
 * 메시지 종류. 요청과 알림 모두 이 이름을 type 필드에 싣는다.
 * 구역별로 나눠 두었으니, 다른 담당은 맨 아래에 추가한다.
 */
public enum MessageType {

    // ===== GM: 클라 -> 서버 요청 =====
    ROLL_DICE,
    CHOOSE_DESTINATION,
    PURCHASE_PROPERTY,
    DECLINE_PROPERTY_PURCHASE,
    BUILD,
    DECLINE_BUILD,
    ACQUIRE_PROPERTY,
    DECLINE_ACQUIRE_PROPERTY,
    SELL_PROPERTIES,

    // ===== GM: 서버 -> 클라 결과 알림 =====
    DICE_ROLLED,
    DESTINATION_CHOSEN,
    CARD_DRAWN,
    PROPERTY_PURCHASED,
    BUILT,
    TOLL_PAID,
    PROPERTY_ACQUIRED,
    PROPERTIES_SOLD,
    PLAYER_BANKRUPT,
    PLAYER_MOVED,
    DONATION_PAID,
    WELFARE_FUND_RECEIVED,
    MONEY_CHANGED,

    // ===== 공통 =====
    ERROR

    // (게임 진행·로비·네트워크 담당 추가분은 아래에)
}