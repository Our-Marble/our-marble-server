package com.sparta.ourmarbleserver.economy.service;


import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.PlayerState;
import org.springframework.stereotype.Service;

/**
 * 현금이 오가는 경제 규칙을 모은다. 상태만 바꾸고, 알림 전송과 저장은 GameService가 한다.
 * 월급, 비용 차감, 현금 이체가 있고, 세무조사, 적립금은 이후 단계에서 추가한다.
 */
@Service
public class EconomyService {
    /** 월급 (출발 지점을 지나면 지급) */
    public static final long SALARY_AMOUNT = 100_000;

    /** 세무조사 세금 (세무조사 칸에 도착하면 부과, 적립금에 쌓임) */
    public static final long TAX_AMOUNT = 100_000;

    /** 월급을 현금에 더한다. 지급한 금액을 돌려준다. */
    public long paySalary(PlayerState player) {
        player.addMoney(SALARY_AMOUNT);
        return SALARY_AMOUNT;
    }

    /** 비용(땅값 등)을 현금에서 뺀다. 잔액이 충분한지는 호출하는 쪽이 먼저 확인한다. */
    public void charge(PlayerState player, long amount) {
        player.addMoney(-amount);
    }

    /** 은행에서 현금을 받는다. (땅 매각 대금 등) */
    public void deposit(PlayerState player, long amount) {
        player.addMoney(amount);
    }

    /** payer의 현금을 amount만큼 receiver에게 옮긴다. 잔액이 충분한지는 호출하는 쪽이 먼저 확인한다. */
    public void transfer(PlayerState payer, PlayerState receiver, long amount) {
        payer.addMoney(-amount);
        receiver.addMoney(amount);
    }

    /**
     * 세금(벌금)을 현금 한도 안에서만 걷어 적립금에 쌓는다. 매각이나 파산은 없다.
     * 실제로 낸 금액을 돌려준다. (예: 세금 100,000인데 현금 30,000이면 30,000만 냄)
     */
    public long payTax(GameState state, PlayerState player, long amount) {
        return payPenalty(state, player, amount, true);
    }

    /** 현금 한도 안에서만 비용을 낸다. (벌금 등, 매각·파산 없음) 실제로 낸 금액을 돌려준다. */
    public long payWithinBalance(PlayerState player, long amount) {
        long paid = Math.min(amount,Math.max(0, player.getMoney()));
        player.addMoney(-paid);
        return paid;
    }

    /** 벌금을 현금 한도 안에서 낸다. toWelfareFund가 true면 적립금에 쌓고, false면 은행으로 간다. */
    public long payPenalty(GameState state, PlayerState player, long amount, boolean toWelfareFund) {
        long paid = payWithinBalance(player, amount);
        if (toWelfareFund) {
            state.setWelfareFund(state.getWelfareFund() + paid);
        }
        return paid;
    }

    /** 쌓인 적립금 전액을 받는다. 받은 금액을 돌려주고 적립금은 0이 된다. */
    public long receiveWelfareFund(GameState state, PlayerState player) {
        long amount = state.getWelfareFund();
        if (amount <= 0) {
            return 0;
        }
        player.addMoney(amount);
        state.setWelfareFund(0);
        return amount;
    }
}
