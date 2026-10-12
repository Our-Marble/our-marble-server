package com.sparta.ourmarbleserver.scheduler.service;

import com.sparta.ourmarbleserver.game.dto.GameMessage;
import com.sparta.ourmarbleserver.game.dto.GameResult;
import com.sparta.ourmarbleserver.game.dto.TileData;
import com.sparta.ourmarbleserver.game.service.GameDataService;
import com.sparta.ourmarbleserver.game.service.GameService;
import com.sparta.ourmarbleserver.game.state.*;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.websocket.WebSocketMessageSender;
import com.sparta.ourmarbleserver.property.service.PropertyService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 제한 시간 안에 입력이 없으면, 서버가 그 phase에 맞는 요청을 대신 호출한다.
 * 사람이 보낸 요청과 똑같이 GameService의 함수를 부르고, 돌려받은 결과를 방 전원에게 보낸다.
 * 대신 진행의 행동: 주사위와 카드는 그대로, 구매·건설·인수는 거절, 매각은 싼 땅부터, 세계여행은 출발 칸으로 간다.
 */

@Service
public class AutoPlayService implements TimerService.Handler {

    /** 세계여행 목적지. 출발 칸이다. (무인도 같은 불리한 칸을 피한다) */
    private static final int DESTINATION_POSITION = 0;

    private final GameService gameService;
    private final GameStateRepository repository;
    private final GameDataService gameDataService;
    private final PropertyService propertyService;
    private final WebSocketMessageSender sender;

    public AutoPlayService(GameService gameService, GameStateRepository repository, GameDataService gameDataService,
                           PropertyService propertyService, WebSocketMessageSender sender, TimerService timerService) {
        this.gameService = gameService;
        this.repository = repository;
        this.gameDataService = gameDataService;
        this.propertyService = propertyService;
        this.sender = sender;
        timerService.setHandler(this);   // 시간이 지나면 이 서비스가 불리도록 타이머에 등록한다
    }

    @Override
    public void onTimeout(String roomId, long playerId, int turnNumber, TurnPhase phase) {
        GameResult result = play(playerId, phase);
        for (GameMessage message : result.messages()) {
            sender.sendToRoom(result.roomId(), message.type(), message.payload());
        }
    }

    private GameResult play(long playerId, TurnPhase phase) {
        GameState state = repository.findByPlayerId(playerId)
                .orElseThrow(() -> new GameException(ErrorCode.INVALID_STATE));
        PlayerState player = state.getPlayerState(playerId);

        return switch (phase) {
            case AWAITING_ROLL -> gameService.rollDice(playerId);
            case AWAITING_DRAW_CARD -> gameService.drawCard(playerId);
            case AWAITING_DESTINATION -> gameService.chooseDestination(playerId, DESTINATION_POSITION);
            case AWAITING_PURCHASE -> gameService.purchaseProperty(playerId, propertyIdUnder(player), false);
            case AWAITING_BUILD -> gameService.build(playerId, propertyIdUnder(player), false);
            case AWAITING_ACQUIRE -> gameService.acquireProperty(playerId, propertyIdUnder(player), false);
            case AWAITING_SELL -> gameService.sellProperties(playerId, chooseSellProperties(state, player));
        };
    }

    /** 플레이어의 말이 서 있는 땅 번호 */
    private int propertyIdUnder(PlayerState player) {
        TileData tile = gameDataService.getTiles().get(player.getPosition());
        if (tile.propertyId() == null) {
            throw new GameException(ErrorCode.INVALID_PROPERTY);
        }
        return tile.propertyId();
    }


    /** 통행료를 낼 수 있을 때까지, 매각가가 싼 땅부터 고른다. */
    private List<Integer> chooseSellProperties(GameState state, PlayerState player) {
        PropertyState tollProperty = state.getPropertyState(propertyIdUnder(player))
                .orElseThrow(() -> new GameException(ErrorCode.INVALID_PROPERTY));
        long toll = propertyService.getToll(tollProperty);

        List<PropertyState> mine = state.properties().stream()
                .filter(property -> property.isOwnedBy(player.getPlayerId()))
                .sorted(Comparator.comparingLong(propertyService::getSellValue))
                .toList();

        List<Integer> selected = new ArrayList<>();
        long money = player.getMoney();
        for (PropertyState property : mine) {
            if (money >= toll) { break; }
            selected.add(property.getPropertyId());
            money += propertyService.getSellValue(property);
        }
        return selected;
    }
}
