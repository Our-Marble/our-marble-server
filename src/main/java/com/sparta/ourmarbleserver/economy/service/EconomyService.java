package com.sparta.ourmarbleserver.economy.service;


import com.sparta.ourmarbleserver.game.state.PlayerState;
import org.springframework.stereotype.Service;

/**
 * 현금이 오가는 경제 규칙을 모은다. 상태만 바꾸고, 알림 전송과 저장은 GameService가 한다.
 * 지금은 월급만 있고, 통행료 이체, 세무조사, 적립금은 이후 단계에서 추가한다.
 */
@Service
public class EconomyService {
    /** 월급 (출발 지점을 지나면 지급) */
    public static final long SALARY_AMOUNT = 100_000;

    /** 월급을 현금에 더한다. 지급한 금액을 돌려준다. */
    public long paySalary(PlayerState player) {
        player.addMoney(SALARY_AMOUNT);
        return SALARY_AMOUNT;
    }
}
