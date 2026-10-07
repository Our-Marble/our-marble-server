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
import com.sparta.ourmarbleserver.property.dto.PropertyPurchasedPayload;
import com.sparta.ourmarbleserver.property.service.PropertyService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;

/**
 * 게임 진행의 중심. 요청을 검증하고, 주사위·이동·턴 서비스를 순서대로 부르고, 상태를 저장하고, 알림을 보낸다.
 * (클라 RollDice → HandleDiceRolled → ProcessArrival 흐름)
 * 현재 처리하는 요청: OLL_DICE, PURCHASE_PROPERTY로, EconomyService. 나머지 요청은 단계별로 추가한다.
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
    private final PropertyService propertyService;
    private final GameDataService gameDataService;
    private final List<TileData> tiles;

    public GameService(GameStateRepository repository, EventPublisher publisher, DiceService diceService,
                       MoveService moveService, TurnService turnService,EconomyService economyService, PropertyService propertyService, GameDataService gameDataService) {
        this.repository = repository;
        this.publisher = publisher;
        this.diceService = diceService;
        this.moveService = moveService;
        this.turnService = turnService;
        this.economyService = economyService;
        this.propertyService = propertyService;
        this.gameDataService = gameDataService;
        this.tiles = gameDataService.getTiles();
    }

    // ==== 요청 받기 ====

    @Override
    public Set<MessageType> types() {
        return Set.of(MessageType.ROLL_DICE, MessageType.PURCHASE_PROPERTY);
    }

    @Override
    public void handle(MessageType type, String roomId, long playerId, JsonNode payload) {
        switch (type) {
            case ROLL_DICE -> rollDice(roomId,playerId);
            case  PURCHASE_PROPERTY -> purchaseProperty(roomId, playerId, requirePropertyId(payload), requireAccept(payload));
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

    // ===== 땅 구매 / 거절 =====

    /**
     * 도착한 빈 땅을 사거나 거절한다. 어느 쪽이든 PROPERTY_PURCHASED를 방 전원에게 보내고 턴을 끝낸다.
     * 구매: 빈 땅 → 현금 확인 → 땅값 차감 → 주인 등록. 거절은 상태 변화 없이 isAccept=false로 알린다.
     */

    public void purchaseProperty(String roomId, long playerId, int propertyId, boolean isAccept) {
        GameState state = validate(roomId, playerId, TurnPhase.AWAITING_PURCHASE, propertyId);
        PlayerState player = state.getPlayerState(playerId);
        requireStandingOn(player, propertyId);

        if(isAccept) {
            PropertyState property = state.getPropertyState(propertyId).orElseThrow();
            if(property.hasOwner()) {
                throw new GameException(ErrorCode.ALREADY_OWNED);
            }
            long price = propertyService.getLandPrice(propertyId);
            if(player.getMoney() < price) {
                throw new GameException(ErrorCode.NOT_ENOUGH_MONEY);
            }

            economyService.charge(player, price);
            property.setOwnerId(playerId);
        }

        publisher.publishToRoom(roomId, MessageType.PROPERTY_PURCHASED,
                new PropertyPurchasedPayload(playerId, propertyId, isAccept));
        turnService.endTurn(state);

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
            settleToll(state,player,property);
        }
    }

    // ===== 통행료 정산 =====

    /**
     * 남의 땅에 도착했을 때 통행료를 내고, 인수할 수 있는지에 따라 다음 phase를 정한다.
     * 통행료와 현금 이체는 알림 없이 서버 내부에서만 처리한다. (클라가 같은 규칙으로 계산)
     * 통행료를 내고도 현금 ≥ 인수가이면 인수 선택을 기다리고, 아니면 턴을 끝낸다.
     */
    private void settleToll(GameState state, PlayerState payer, PropertyState property) {
        long toll = propertyService.getToll(property);
        if (payer.getMoney() < toll) {
            //TODO(경제): 현금부족 -> 매각(AWAITING_SELL) 또는 파산. 그때까지 턴만 넘긴다.
            turnService.endTurn(state);
            return;
        }

        PlayerState owner = state.getPlayerState(property.getOwnerId());
        economyService.transfer(payer, owner, toll);

        if (payer.getMoney() >= propertyService.getAcquireValue(property)) {
            state.setPhase(TurnPhase.AWAITING_ACQUIRE);
        } else {
            turnService.endTurn(state);
        }
    }

    // ===== 요청 본문 읽기 =====

    /** payload에서 propertyId를 읽는다. 없거나 숫자가 아니면 올바르지 않은 땅으로 거부한다. */

    private static int requirePropertyId(JsonNode payload) {
        JsonNode node = payload == null ? null : payload.get("propertyId");
        if(node == null || !node.isNumber()) {
            throw new GameException(ErrorCode.INVALID_PROPERTY);
        }
        return node.intValue();
    }

    /** payload에서 isAccept를 읽는다. 빠졌으면 실수로 사거나 거절하지 않도록 거부한다. */
    private static boolean requireAccept (JsonNode payload) {
        JsonNode node = payload == null ? null : payload.get("isAccept");
        if(node == null || !node.isBoolean()) {
            throw new GameException(ErrorCode.INVALID_STATE);
        }
        return node.booleanValue();
    }

    // ===== 공통 검증 =====

    private GameState validate(String roomId, long playerId, TurnPhase requiredPhase) {
        return validate(roomId, playerId, requiredPhase, null);
    }

    /**
     * 모든 요청의 공통 검증: 방 존재 → 게임 진행 중 → 내 차례 → 파산 여부 → 요청이 현재 phase에 맞는지.
     * 상태를 바꾸기 전에 호출하므로 실패해도 상태는 그대로다.
     */
    private GameState validate(String roomId, long playerId, TurnPhase requiredPhase, Integer propertyId) {
        GameState state = repository.findById(roomId)
                .orElseThrow(() -> new GameException(ErrorCode.INVALID_STATE));
        if (state.isGameOver()) {
            throw new GameException(ErrorCode.INVALID_STATE);
        }
        if (propertyId != null && state.getPropertyState(propertyId).isEmpty()) {
            throw new GameException(ErrorCode.INVALID_PROPERTY);
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

    /** 내 말이 요청한 땅 위에 서 있는지 확인한다. 아니면 올바르지 않은 땅으로 거부한다. */
    private void requireStandingOn (PlayerState player, int propertyId) {
        TileData tile = tiles.get(player.getPosition());
        if (!"PROPERTY".equals(tile.type()) || tile.propertyId() == null || tile.propertyId() != propertyId) {
            throw new GameException(ErrorCode.INVALID_PROPERTY);
        }
    }

}
