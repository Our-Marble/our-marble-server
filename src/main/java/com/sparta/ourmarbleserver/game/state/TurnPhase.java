package com.sparta.ourmarbleserver.game.state;

/**
 * 턴 단계. 선택지 프롬프트는 클라가 띄우므로, 서버는 이 값과 말 위치로
 * 요청을 받을 수 있는 상태인지 판단한다.
 */
public enum TurnPhase {
    MOVING,           // 이동 중 (월급 판정 포함)
    AWAITING_ACTION,  // 도착 후 부동산 행동(구매·건설·인수 또는 거절) 대기
    AWAITING_SELL,    // 통행료가 현금보다 많아 매각 요청 대기
    TURN_END          // 턴 종료
}