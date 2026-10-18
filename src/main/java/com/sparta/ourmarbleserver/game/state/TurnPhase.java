package com.sparta.ourmarbleserver.game.state;

/**
 * 턴 단계. 선택지 프롬프트는 클라가 띄우므로, 서버는 이 값으로
 * 요청을 받을 수 있는 상태인지 판단한다.
 * 서버에는 연출이 없어서 요청 하나가 흐름을 끝까지 처리하고 끝난 뒤의 phase만 저장한다.
 * 그래서 이동 중 phase는 두지 않는다.
 */
public enum TurnPhase {
    AWAITING_ROLL,        // 주사위 굴리기 대기 (턴 시작)
    AWAITING_DESTINATION, // 세계여행 목적지 선택 대기 (턴 시작)
    AWAITING_PURCHASE,    // 도착 후 구매 또는 거절 대기
    AWAITING_BUILD,       // 도착 후 건설 또는 거절 대기
    AWAITING_ACQUIRE,     // 도착 후 인수 또는 거절 대기
    AWAITING_SELL,        // 통행료가 현금보다 많아 매각 요청 대기
    AWAITING_DRAW_CARD    // 황금열쇠 칸 도착 후 카드 뽑기 대기
}