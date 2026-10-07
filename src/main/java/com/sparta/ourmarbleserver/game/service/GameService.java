package com.sparta.ourmarbleserver.game.service;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.dto.DiceRolledPayload;
import com.sparta.ourmarbleserver.game.dto.PropertyData;
import com.sparta.ourmarbleserver.game.dto.TileData;
import com.sparta.ourmarbleserver.game.state.*;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.transport.EventPublisher;
import com.sparta.ourmarbleserver.global.transport.MessageHandler;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 게임 진행의 중심. 요청을 검증하고, 주사위·이동·턴 서비스를 순서대로 부르고, 상태를 저장하고, 알림을 보낸다.
 * (클라 RollDice → HandleDiceRolled → ProcessArrival 흐름)
 * 현재 처리하는 요청: ROLL_DICE. 나머지 요청은 단계별로 추가한다.
 */
@Service
public class GameService implements MessageHandler {

    /** 초기 자금 */
    public static final long START_MONEY = 500_000;
    private final GameStateRepository repository;
    private final EventPublisher publisher;
    private final DiceService diceService;
    private final MoveService moveService;
    private final TurnService turnService;
    private final EconomyService economyService;
    private final GameDataService gameDataService;
    private final List<TileData> tiles;

    public GameService(GameStateRepository repository, EventPublisher publisher, DiceService diceService,
                       MoveService moveService, TurnService turnService,EconomyService economyService, GameDataService gameDataService) {
        this.repository = repository;
        this.publisher = publisher;
        this.diceService = diceService;
        this.moveService = moveService;
        this.turnService = turnService;
        this.economyService = economyService;
        this.gameDataService = gameDataService;
        this.tiles = gameDataService.getTiles();
    }

    // ==== 요청 받기 ====

    @Override
    public Set<MessageType> types() {
        return Set.of(MessageType.ROLL_DICE);
    }

    @Override
    public void handle(MessageType type, String roomId, long playerId, JsonNode payload) {
        switch (type) {
            case ROLL_DICE -> rollDice(roomId,playerId);
            default -> throw new IllegalStateException("GameService가 처리하지 않는 요청입니다.: " + type);
        }
    }

    // ===== 게임 시작 (로비 담당 연동 전 임시) =====

    /** 방 상태를 만들고 첫 플레이어의 턴을 시작한다. playerIds의 순서가 턴 순서다. */
    public GameState startGame(String roomId, List<Long> playerIds) {
        GameState state = new GameState(roomId);
        for(long id : playerIds) {
            PlayerState player = new PlayerState(id);
            player.setMoney(START_MONEY);
            state.addPlayer(player);
        }
        state.setPlayerOrder(playerIds);
        for (PropertyData data : gameDataService.getProperties()) {
            state.addProperty(new PropertyState(data.id()));
        }

        turnService.startTurn(state, playerIds.get(0));
        repository.save(state);
        return state;
    }

    // ===== 주사위 굴리기 =====

    /**
     * 주사위를 굴리고 이동한 뒤 도착 칸에 따라 다음 phase를 정한다.
     * 알림은 DICE_ROLLED 하나만 나간다. 이동과 월급은 클라가 같은 규칙으로 계산한다.
     */
    public void rollDice(String roomId, long playerId) {
        GameState state = validate(roomId, playerId, TurnPhase.AWAITING_ROLL);
        PlayerState player = state.getPlayerState(playerId);

        DiceService.DiceResult dice = diceService.roll(state);
        publisher.publishToRoom(roomId, MessageType.DICE_ROLLED,
                new DiceRolledPayload(playerId, dice.dice1(), dice.dice2()));

        // TODO(특수칸): 3연속 더블이면 이동 없이 무인도로, 무인도에 있으면 더블 탈출 처리

        MoveService.MoveResult move = moveService.moveBy(player, dice.sum());
        if (move.passedStart()) {
            economyService.paySalary(player);
        }
        processArrival(state, player);

        repository.save(state);
    }

    // ===== 도착 칸 처리 =====

    private void processArrival(GameState state, PlayerState player) {
        TileData tile = tiles.get(player.getPosition());
        switch (tile.type()) {
            case "PROPERTY" -> arriveAtProperty(state, player, tile.propertyId());
            case "GOLDEN_KEY" -> state.setPhase(TurnPhase.AWAITING_DRAW_CARD);

            // TODO(특수칸): 출발(효과없음), 무인도, 기부금 수령(적립금), 세무조사, 세계여행, 그때까지만 턴만 넘긴다.

            default -> turnService.endTurn(state);
        }
    }

    private void arriveAtProperty(GameState state, PlayerState player, int propertyId) {
        PropertyState property = state.getPropertyState(propertyId)
                .orElseThrow(() -> new IllegalStateException("땅 상태가 없습니다." + propertyId));

        if (!property.hasOwner()) {
            state.setPhase(TurnPhase.AWAITING_PURCHASE);
        } else if (property.isOwnedBy(player.getPlayerId())) {
            state.setPhase(TurnPhase.AWAITING_BUILD);
        } else {
            //TODO(경제): 통행료 정산 -> 인수 선택 / 매각 / 파산. 그때까지는 턴만 넘긴다.
            turnService.endTurn(state);
        }
    }

    // ===== 공통 검증 =====

    /**
     * 모든 요청의 공통 검증: 방 존재 → 게임 진행 중 → 내 차례 → 파산 여부 → 요청이 현재 phase에 맞는지.
     * 상태를 바꾸기 전에 호출하므로 실패해도 상태는 그대로다.
     */
    private GameState validate(String roomId, long playerId, TurnPhase requiredPhase) {
        GameState state = repository.findById(roomId)
                .orElseThrow(() -> new GameException(ErrorCode.INVALID_STATE));
        if (state.isGameOver()) {
            throw new GameException(ErrorCode.INVALID_STATE);
        }
        if (state.getCurrentPlayerId() != playerId) {
            throw new GameException(ErrorCode.NOT_YOUR_TURN);
        }
        if (state.getPlayerState(playerId).isBankrupt()) {
            throw new GameException(ErrorCode.PLAYER_BANKRUPT);
        }
        if (state.getPhase() != requiredPhase) {
            throw new GameException(ErrorCode.INVALID_STATE);
        }
        return state;
    }






}
