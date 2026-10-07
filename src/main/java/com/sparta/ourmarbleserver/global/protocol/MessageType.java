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
    ERROR,

    // (게임 진행·로비·네트워크 담당 추가분은 아래에)

    // ===== 로비: 클라 -> 서버 요청 (topic: LOBBY) =====
    CREATE_ROOM,
    ENTER_ROOM,
    LEAVE_ROOM,
    START_GAME,
    REFRESH_ROOM_LIST, // 로비 새로고침 버튼

    // ===== 로비: 서버 -> 클라 알림 (topic: LOBBY) =====
    ROOM_LIST,         // 본인에게: 방 목록 전체 (로비 진입 시 자동 + 새로고침 응답)
    ROOM_ENTERED,      // 본인에게: 방 생성·입장 성공 (대기창 전환 신호)
    ROOM_UPDATED,      // lobby/room/{id} 전원: 인원·방장 변경

    // ===== 로비 -> 게임 전환: 서버 -> 클라 알림 (topic: GAME) =====
    GAME_STARTED       // game/room/{id} 전원: 게임창 전환 신호
}