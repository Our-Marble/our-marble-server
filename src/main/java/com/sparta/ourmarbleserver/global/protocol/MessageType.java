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
    GAME_STATE_REQUEST, // 현재 게임 상태 요청 (재접속 시 화면 복구)

    // ===== GM: 서버 -> 클라 결과 알림 =====
    BUILT,
    CARD_DRAWN,
    DESTINATION_CHOSEN,
    DICE_ROLLED,
    PROPERTIES_SOLD,
    PROPERTY_ACQUIRED,
    PROPERTY_PURCHASED,
    GAME_STATE_SYNC,    // 본인에게: 현재 게임 상태 전체 (재접속 시 화면 복구)

    // ===== 공통 =====
    ERROR,


    // (게임 진행·로비·네트워크 담당 추가분은 아래에)

    // ===== 로비: 클라 -> 서버 요청 (topic: LOBBY) =====
    CREATE_ROOM,
    ENTER_ROOM,
    LEAVE_ROOM,
    SET_READY,         // 준비 / 준비 취소
    START_GAME,

    // ===== 로비: 서버 -> 클라 알림 (topic: LOBBY) =====
    CONNECTED,         // 본인에게: 접속 완료 (playerId, nickname)
    ROOM_ENTERED,      // 본인에게: 방 생성·입장 성공 (대기창 전환 신호)
    ROOM_LEFT,         // 본인에게: 방 나가기 성공
    ROOM_LIST,         // 본인에게: 방목록
    ROOM_UPDATED,      // 방 전원: 인원·준비·방장 변경
    GAME_STARTED     // 방 전원: 게임창 전환 신호
}