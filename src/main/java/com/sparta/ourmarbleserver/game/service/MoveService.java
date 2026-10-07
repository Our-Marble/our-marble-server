package com.sparta.ourmarbleserver.game.service;

import com.sparta.ourmarbleserver.game.state.PlayerState;
import lombok.Getter;
import org.springframework.stereotype.Service;

@Getter
@Service
public class MoveService {

    /** 이동 결과. passedStart는 출발 지점을 지났거나 출발 칸에 도착했는지(월급 대상)다. */
    public record MoveResult(int from, int to, boolean passedStart) {}

    private final int tileCount;

    public  MoveService(GameDataService gameDataService) {
        this.tileCount = gameDataService.getTiles().size();
    }

    /** 주사위 눈만큼 앞으로 이동한다. 이동한 칸 번호가 출발 때보다 작으면 출발 지점을 지난 것이다. */
    public  MoveResult moveBy(PlayerState player, int steps) {
        int from = player.getPosition();
        int to = (from + steps) % tileCount;

        player.setPosition(to);
        return new MoveResult(from, to, to < from);
    }

    /** 칸으로 바로 이동한다. 월급은 없다. (무인도 이동, 세계여행, 뒤로가는 카드) */
    public MoveResult moveDirectly(PlayerState player, int target) {
        int from = player.getPosition();
        player.setPosition(target);
        return new MoveResult(from, target, false);
    }

}
