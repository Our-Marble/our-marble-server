package com.sparta.ourmarbleserver.scheduler.service;

import com.sparta.ourmarbleserver.bot.service.IBotStrategy;
import com.sparta.ourmarbleserver.game.dto.GameMessage;
import com.sparta.ourmarbleserver.game.dto.GameResult;
import com.sparta.ourmarbleserver.game.dto.TileData;
import com.sparta.ourmarbleserver.game.service.GameDataService;
import com.sparta.ourmarbleserver.game.service.GameService;
import com.sparta.ourmarbleserver.game.state.*;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.GameErrorCode;
import com.sparta.ourmarbleserver.global.websocket.WebSocketMessageSender;
import com.sparta.ourmarbleserver.property.domain.BuildingLevel;
import com.sparta.ourmarbleserver.property.service.PropertyService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 서버가 플레이어를 대신해 그 phase의 요청을 호출한다. 대상은 봇이거나, 제한 시간 안에 입력이 없어 AFK가 된 사람이다.
 * 사람이 보낸 요청과 똑같이 GameService의 함수를 부르고, 돌려받은 결과를 방 전원에게 보낸다.
 * 주사위와 카드는 그대로 하고, 구매·건설·인수·매각·세계여행은 봇 전략(IBotStrategy)에게 물어서 정한다.
 * (봇은 항상 이 방식으로 진행하고, 사람은 AFK일 때만 이 방식으로 진행한다)
 */
@Service
public class AutoPlayService implements TimerService.Handler {

    private final GameService gameService;
    private final GameStateRepository repository;
    private final GameDataService gameDataService;
    private final PropertyService propertyService;
    private final IBotStrategy strategy;
    private final WebSocketMessageSender sender;
    private final TimerService timerService;

    public AutoPlayService(GameService gameService, GameStateRepository repository, GameDataService gameDataService,
                           PropertyService propertyService, IBotStrategy strategy, WebSocketMessageSender sender,
                           TimerService timerService) {
        this.gameService = gameService;
        this.repository = repository;
        this.gameDataService = gameDataService;
        this.propertyService = propertyService;
        this.strategy = strategy;
        this.sender = sender;
        this.timerService = timerService;
        timerService.setHandler(this);   // 시간이 지나면 이 서비스가 불리도록 타이머에 등록한다
    }

    /**
     * 마감시간이 지난 방의, 지금 저장된 상태의 현재 플레이어와 phase를 대신 진행한다.
     * (타이머에는 누구의 어떤 phase인지 기록하지 않는다. 상태 저장과 마감시간 기록 사이에 서버가 죽어도 어긋나지 않게 하려는 것이다)
     * 진행 중인 게임이 없으면 남은 타이머를 지운다.
     */
    @Override
    public void onTimeout(String roomId) {
        Optional<GameState> state = repository.findById(roomId).filter(found -> !found.isGameOver());
        if (state.isEmpty()) {
            timerService.cancel(roomId);
            return;
        }
        GameResult result = play(state.get().getCurrentPlayerId(), state.get().getPhase());
        for (GameMessage message : result.messages()) {
            sender.sendToRoom(result.roomId(), message.type(), message.payload());
        }
    }

    private GameResult play(long playerId, TurnPhase phase) {
        GameState state = repository.findByPlayerId(playerId)
                .orElseThrow(() -> new GameException(GameErrorCode.INVALID_STATE));
        PlayerState player = state.getPlayerState(playerId);

        return switch (phase) {
            case AWAITING_ROLL -> gameService.rollDice(playerId);
            case AWAITING_DRAW_CARD -> gameService.drawCard(playerId);
            case AWAITING_DESTINATION -> gameService.chooseDestination(playerId, strategy.chooseTravelDestination(state, playerId));
            case AWAITING_PURCHASE -> gameService.purchaseProperty(playerId, propertyIdUnder(player), wantsPurchase(state, player));
            case AWAITING_BUILD -> gameService.build(playerId, propertyIdUnder(player), wantsBuild(state, player));
            case AWAITING_ACQUIRE -> gameService.acquireProperty(playerId, propertyIdUnder(player), wantsAcquire(state, player));
            case AWAITING_SELL -> gameService.sellProperties(playerId, chooseSellProperties(state, player));
        };
    }

    /** 땅을 살지. 전략이 판단한다. */
    private boolean wantsPurchase(GameState state, PlayerState player) {
        int propertyId = propertyIdUnder(player);
        return strategy.shouldPurchase(state, player.getPlayerId(), propertyId, propertyService.getLandPrice(propertyId));
    }

    /** 건설할지. 건설 불가 땅과 호텔은 전략에 묻지 않고 거절한다. */
    private boolean wantsBuild(GameState state, PlayerState player) {
        int propertyId = propertyIdUnder(player);
        PropertyState property = state.getPropertyState(propertyId)
                .orElseThrow(() -> new GameException(GameErrorCode.INVALID_PROPERTY));
        if (!propertyService.canBuild(propertyId) || property.getBuildingLevel() == BuildingLevel.HOTEL) { return false;}
        BuildingLevel next = BuildingLevel.fromIndex(property.getBuildingLevel().index() + 1);
        return strategy.shouldBuild(state, player.getPlayerId(), propertyId, propertyService.getBuildCost(propertyId, next));
    }

    /** 인수할지. 전략이 판단한다. */
    private boolean wantsAcquire(GameState state, PlayerState player) {
        int propertyId = propertyIdUnder(player);
        PropertyState property = state.getPropertyState(propertyId)
                .orElseThrow(() -> new GameException(GameErrorCode.INVALID_PROPERTY));
        return strategy.shouldAcquire(state, player.getPlayerId(), propertyId, propertyService.getAcquireValue(property));
    }

    /** 플레이어의 말이 서 있는 땅 번호 */
    private int propertyIdUnder(PlayerState player) {
        TileData tile = gameDataService.getTiles().get(player.getPosition());
        if (tile.propertyId() == null) {
            throw new GameException(GameErrorCode.INVALID_PROPERTY);
        }
        return tile.propertyId();
    }


    /** 통행료를 낼 수 있을 때까지 팔 땅을 전략이 고른다. */
    private List<Integer> chooseSellProperties(GameState state, PlayerState player) {
        PropertyState tollProperty = state.getPropertyState(propertyIdUnder(player))
                .orElseThrow(() -> new GameException(GameErrorCode.INVALID_PROPERTY));
        return strategy.chooseSellProperties(state, player.getPlayerId(), propertyService.getToll(tollProperty));
    }
}