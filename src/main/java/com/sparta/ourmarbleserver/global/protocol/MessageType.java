package com.sparta.ourmarbleserver.global.protocol;

/**
 * 메시지 종류. 요청과 알림 모두 이 이름을 type 필드에 싣는다.
 * 구역별로 나눠 두었으니, 다른 담당은 맨 아래에 추가한다.
 */
public enum MessageType {

    // ===== GM: 요청과 알림이 같은 type 이름을 쓰는 것 =====
    ROLL_DICE,            // 요청: 주사위 굴리기 / 알림: 주사위 결과
    BUILD,                // 요청: 건설·거절 / 알림: 건설·거절 결과
    DRAW_CARD,            // 요청: 카드 뽑기 / 알림: 뽑은 카드

    // ===== GM: 클라 -> 서버 요청 (거절은 별도 요청 없이 isAccept 값으로 보낸다) =====
    CHOOSE_DESTINATION,
    PURCHASE_PROPERTY,
    ACQUIRE_PROPERTY,
    SELL_PROPERTIES,

    // ===== GM: 서버 -> 클라 결과 알림 =====
    DESTINATION_CHOSEN,
    PROPERTY_PURCHASED,
    PROPERTY_ACQUIRED,
    PROPERTIES_SOLD,

    // ===== 공통 =====
    ERROR

    // (게임 진행·로비·네트워크 담당 추가분은 아래에)
}