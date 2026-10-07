package com.sparta.ourmarbleserver.game.service;

import com.sparta.ourmarbleserver.game.state.PlayerState;
import org.springframework.stereotype.Service;

@Service
public class MoveService {
    /** 월급 (출발 지점을 지나면 지급*/
    public static final long SALARY_AMOUNT = 100_000;

    /** 이동 결과. salary는 이번 이동으로 받는 월급이고, 못 받았다면 0이다. */
    public record MoveResult(int from, int to, long salary) {}

    private final int tileCount;

    public  MoveService(GameDataService gameDataService) {
        this.tileCount = gameDataService.getTiles().size();
    }

    /**
     * 주사위 눈만큼 앞으로 이동한다. 이동한 칸 번호가 출발 때보다 작으면 출발 지점을 지난 것이라 월급을 준다.
     * 월급은 이동 직전에 현금에 더한다. (클라와 같은 시점)
     */
    public  MoveResult moveBy(PlayerState player, int steps) {
        int from = player.getPosition();
        int to = (from + steps) % tileCount;

        long salary = 0;
        if (to < from) {
            salary = SALARY_AMOUNT;
            player.addMoney(salary);
        }

        player.setPosition(to);
        return new MoveResult(from, to, salary);
    }

    /** 칸으로 바로 이동한다. 월급은 없다. (무인도 이동, 세계여행, 뒤로가는 카드) */
    public MoveResult moveDirectly(PlayerState player, int target) {
        int from = player.getPosition();
        player.setPosition(target);
        return new MoveResult(from, target, 0);
    }

    public int getTileCount() {
        return tileCount;
    }
}
