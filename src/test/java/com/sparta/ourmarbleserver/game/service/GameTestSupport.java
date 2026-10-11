package com.sparta.ourmarbleserver.game.service;

import java.util.Random;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.state.GameStateRepository;
import com.sparta.ourmarbleserver.property.service.PropertyService;

/**
 * 테스트에서 GameService를 만드는 도우미. 서비스 구성이 바뀌어도 이 파일만 고치면 된다.
 * 주사위 눈이 필요하면 FixedRandom을 넘긴다.
 */
final class GameTestSupport {

    private GameTestSupport() {
    }

    /** 주사위가 무작위로 나오는 GameService */
    static GameService newGameService(GameStateRepository repository, GameDataService data) {
        return newGameService(repository, data, new Random());
    }

    /** 주사위 눈과 카드 뽑기가 random으로 정해지는 GameService */
    static GameService newGameService(GameStateRepository repository, GameDataService data, Random random) {
        TurnService turnService = new TurnService(data);
        EconomyService economyService = new EconomyService();
        PropertyService propertyService = new PropertyService(data);
        return new GameService(repository, new DiceService(random), new MoveService(data),
                turnService, economyService, propertyService, data,
                new ArrivalService(turnService, economyService, propertyService, data));
    }
}