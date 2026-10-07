package com.sparta.ourmarbleserver.game.service;

import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.dto.DestinationChosenPayload;
import com.sparta.ourmarbleserver.game.dto.DiceRolledPayload;
import com.sparta.ourmarbleserver.game.dto.PropertyData;
import com.sparta.ourmarbleserver.game.dto.TileData;
import com.sparta.ourmarbleserver.game.state.*;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.transport.EventPublisher;
import com.sparta.ourmarbleserver.global.transport.MessageHandler;
import com.sparta.ourmarbleserver.property.domain.BuildingLevel;
import com.sparta.ourmarbleserver.property.dto.BuiltPayload;
import com.sparta.ourmarbleserver.property.dto.PropertiesSoldPayload;
import com.sparta.ourmarbleserver.property.dto.PropertyAcquiredPayload;
import com.sparta.ourmarbleserver.property.dto.PropertyPurchasedPayload;
import com.sparta.ourmarbleserver.property.service.PropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 게임 진행의 중심. 요청을 검증하고, 주사위·이동·턴 서비스를 순서대로 부르고, 상태를 저장하고, 알림을 보낸다.
 * (클라 RollDice → HandleDiceRolled → ProcessArrival 흐름)
 * 현재 처리하는 요청: OLL_DICE, PURCHASE_PROPERTY로, EconomyService. 나머지 요청은 단계별로 추가한다.
 */
@Service
@RequiredArgsConstructor
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

    // ==== 요청 받기 ====

    @Override
    public Set<MessageType> types() {
        return Set.of(MessageType.ROLL_DICE, MessageType.PURCHASE_PROPERTY, MessageType.SELL_PROPERTIES,
                MessageType.BUILD, MessageType.ACQUIRE_PROPERTY, MessageType.CHOOSE_DESTINATION);
    }

    @Override
    public void handle(MessageType type, String roomId, long playerId, JsonNode payload) {
        switch (type) {
            case ROLL_DICE -> rollDice(roomId,playerId);
            case PURCHASE_PROPERTY -> purchaseProperty(roomId, playerId, requirePropertyId(payload), requireAccept(payload));
            case SELL_PROPERTIES -> sellProperties(roomId, playerId, requirePropertyIds(payload));
            case BUILD -> build(roomId, playerId, requirePropertyId(payload), requireAccept(payload));
            case ACQUIRE_PROPERTY -> acquireProperty(roomId, playerId, requirePropertyId(payload), requireAccept(payload));
            case CHOOSE_DESTINATION -> chooseDestination(roomId,playerId,requireDestination(payload));
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

        turnService.startTurn(state, playerIds.getFirst());
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

    // ===== 땅 매각 =====

    /**
     * 통행료가 모자랄 때 땅을 팔아 현금을 채우고 통행료를 낸다. 매각 뒤에는 인수 선택 없이 턴을 끝낸다.
     * 검증 순서: 목록이 비었거나 중복(INVALID_PROPERTY_LIST) → 없는 땅(INVALID_PROPERTY) → 내 땅 아님(NOT_OWNER)
     * → 현금 + 매각가 합계 < 통행료(NOT_ENOUGH_SELL). 땅은 주인 없음 + 건설 단계 0으로 초기화된다.
     */
    public void sellProperties(String roomId, long playerId, List<Integer> propertyIds) {
        GameState state = validate(roomId, playerId, TurnPhase.AWAITING_SELL);
        PlayerState payer = state.getPlayerState(playerId);

        if (propertyIds.isEmpty() || new HashSet<>(propertyIds).size() != propertyIds.size()) {
            throw new GameException(ErrorCode.INVALID_PROPERTY_LIST);
        }
        List<PropertyState> selected = new ArrayList<>();
        for (int propertyId : propertyIds) {
            PropertyState property = state.getPropertyState(propertyId)
                    .orElseThrow(() -> new GameException(ErrorCode.INVALID_PROPERTY));
            if (!property.isOwnedBy(playerId)) {
                throw new GameException(ErrorCode.NOT_OWNER);
            }
            selected.add(property);
        }

        PropertyState tollProperty = findTollProperty(state, payer);
        long toll = propertyService.getToll(tollProperty);
        long proceeds = selected.stream().mapToLong(propertyService::getSellValue).sum();
        if (payer.getMoney() + proceeds < toll) {
            throw new GameException(ErrorCode.NOT_ENOUGH_SELL);
        }

        // 검증이 모두 끝났으니 상태를 바꾼다,
        selected.forEach(PropertyState::reset);
        economyService.deposit(payer, proceeds);
        economyService.transfer(payer, state.getPlayerState(tollProperty.getOwnerId()), toll);


        publisher.publishToRoom(roomId, MessageType.PROPERTIES_SOLD, new PropertiesSoldPayload(playerId, propertyIds));
        turnService.endTurn(state);

        repository.save(state);

    }

    /** 매각 대기 중인 플레이어가 통행료를 내야 하는 땅(내 말이 서 있는 남의 땅)을 찾는다. 아니면 상태 오류다. */
    private PropertyState findTollProperty(GameState state, PlayerState payer) {
        TileData tile = tileAt(payer.getPosition());
        if (!"PROPERTY".equals(tile.type()) || tile.propertyId() == null) {
            throw new GameException(ErrorCode.INVALID_STATE);
        }
        PropertyState property = state.getPropertyState(tile.propertyId())
                .orElseThrow(() -> new GameException(ErrorCode.INVALID_STATE));
        if (!property.hasOwner() || property.isOwnedBy(payer.getPlayerId())) {
            throw new GameException(ErrorCode.INVALID_STATE);
        }
        return property;
    }

    // ===== 건설 / 거절 =====

    /**
     * 내 땅에 도착했을 때 한 단계 건설하거나 거절한다. 어느 쪽이든 BUILT를 방 전원에게 보내고 턴을 끝낸다.
     * 건설 검증: 내 땅(NOT_OWNER) → 건설 가능 땅(CANNOT_BUILD) → 호텔 아님(MAX_LEVEL) → 현금 ≥ 건설비(NOT_ENOUGH_MONEY).
     * 건물은 한 단계씩 올라가고 건설비는 올라갈 단계의 비용이다. 거절은 상태 변화 없이 isAccept=false로 알린다.
     */
    public void build(String roomId, long playerId, int propertyId, boolean isAccept) {
        GameState state = validate(roomId, playerId, TurnPhase.AWAITING_BUILD, propertyId);
        PlayerState player = state.getPlayerState(playerId);
        requireStandingOn(player, propertyId);

        if(isAccept) {
            PropertyState property = state.getPropertyState(propertyId).orElseThrow();
            if (!property.isOwnedBy(playerId)) {
                throw new GameException(ErrorCode.NOT_OWNER);
            }
            if (!propertyService.canBuild(propertyId)) {
                throw new GameException(ErrorCode.CANNOT_BUILD);
            }
            if (property.getBuildingLevel() == BuildingLevel.HOTEL) {
                throw new GameException(ErrorCode.MAX_LEVEL);
            }
            BuildingLevel nextLevel = BuildingLevel.fromIndex(property.getBuildingLevel().index() + 1);
            long cost = propertyService.getBuildCost(propertyId, nextLevel);
            if (player.getMoney() < cost) {
                throw new GameException(ErrorCode.NOT_ENOUGH_MONEY);
            }

            economyService.charge(player, cost);
            property.setBuildingLevel(nextLevel);
        }

        publisher.publishToRoom(roomId, MessageType.BUILT, new BuiltPayload(playerId, propertyId, isAccept));
        turnService.endTurn(state);

        repository.save(state);
    }

    // ===== 인수 / 거절 =====

    /**
     * 통행료를 낸 뒤 남의 땅을 인수하거나 거절한다. 어느 쪽이든 PROPERTY_ACQUIRED를 방 전원에게 보내고 턴을 끝낸다.
     * 인수 검증: 남의 땅(CANNOT_ACQUIRE) → 현금 ≥ 인수가(NOT_ENOUGH_MONEY). 인수가는 이전 주인에게 이체하고
     * 건물 단계는 그대로 둔 채 주인만 바꾼다. 거절은 상태 변화 없이 isAccept=false로 알린다.
     */
    public void acquireProperty(String roomId, long playerId, int propertyId, boolean isAccept) {
        GameState state = validate(roomId, playerId, TurnPhase.AWAITING_ACQUIRE, propertyId);
        PlayerState player = state.getPlayerState(playerId);
        requireStandingOn(player, propertyId);

        if(isAccept) {
            PropertyState property = state.getPropertyState(propertyId).orElseThrow();
            if (!property.hasOwner() || property.isOwnedBy(playerId)) {
                throw new GameException(ErrorCode.CANNOT_ACQUIRE);
            }
            long price = propertyService.getAcquireValue(property);
            if (player.getMoney() < price) {
                throw new GameException(ErrorCode.NOT_ENOUGH_MONEY);
            }

            economyService.transfer(player, state.getPlayerState(property.getOwnerId()), price); // 주인이 바뀌기 전에 이체
            property.setOwnerId(playerId);
        }

        publisher.publishToRoom(roomId, MessageType.PROPERTY_ACQUIRED,
                new PropertyAcquiredPayload(playerId, propertyId, isAccept));
        turnService.endTurn(state);

        repository.save(state);
    }

    // ===== 세계여행 =====

    /**
     * 세계여행 칸에서 시작한 턴에 목적지를 골라 월급 없이 이동하고, 도착한 칸을 처리한다.
     * DESTINATION_CHOSEN을 방 전원에게 보낸다. 목적지는 보드 안의 칸이면 어디든 고를 수 있다.
     */
    public void chooseDestination(String roomId, long playerId, int destinationPosition) {
        GameState state = validate(roomId, playerId, TurnPhase.AWAITING_DESTINATION);
        PlayerState player = state.getPlayerState(playerId);

        if (destinationPosition < 0 || destinationPosition >= moveService.getTileCount()) {
            throw new GameException(ErrorCode.INVALID_PROPERTY);
        }

        publisher.publishToRoom(roomId, MessageType.DESTINATION_CHOSEN,
                new DestinationChosenPayload(playerId, destinationPosition));

        moveService.moveDirectly(player, destinationPosition);
        processArrival(state, player);

        repository.save(state);
    }

    // ===== 도착 칸 처리 =====

    private void processArrival(GameState state, PlayerState player) {
        TileData tile = tileAt(player.getPosition());
        switch (tile.type()) {
            case "PROPERTY" -> arriveAtProperty(state, player, tile.propertyId());
            case "GOLDEN_KEY" -> state.setPhase(TurnPhase.AWAITING_DRAW_CARD);
            case "START" -> turnService.endTurn(state); //효과 없음(월급은 이동할 때 이미 지급됨)
            case "CHARITY" -> {
                economyService.receiveWelfareFund(state, player);
                turnService.endTurn(state);
            }
            case "DONATION" -> {
                economyService.payTax(state, player, EconomyService.TAX_AMOUNT);
                turnService.endTurn(state);
            }
            case "WORLD_TRAVEL" -> turnService.passTurn(state); // 더블이어도 강제로 턴을 넘긴다.

            // TODO(특수칸): 무인도. 그때까지는 턴만 넘긴다.

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
            long sellable = propertyService.getTotalSellValue(state, payer.getPlayerId());
            if (payer.getMoney() + sellable >= toll) {
                state.setPhase(TurnPhase.AWAITING_SELL);
            } else {
                declareBankrupt(state, payer, state.getPlayerState(property.getOwnerId()));
            }
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

    // ===== 파산 =====

    /**
     * 통행료를 낼 수 없을 때(전부 팔아도 부족) 파산 처리한다. (클라 HandleBankruptcy와 같은 규칙)
     * 가진 땅을 모두 매각가로 현금화하고, 현금 전액을 수납자에게 넘기고, 땅은 주인 없음 + 건설 단계 0으로 초기화한다.
     * 파산은 클라에 알리지 않고 서버 내부에서만 처리한다. 더블이어도 턴은 다음 플레이어로 넘어간다.
     * 게임이 끝나면(생존자 1명) turnService.passTurn은 아무것도 바꾸지 않는다.
     */
    private void declareBankrupt(GameState state, PlayerState payer, PlayerState receiver) {
        long payerId = payer.getPlayerId();
        long liquidated = propertyService.getTotalSellValue(state, payerId);
        state.properties().stream()
                .filter(property -> property.isOwnedBy(payerId))
                .forEach(PropertyState::reset);

        economyService.deposit(payer, liquidated);
        economyService.transfer(payer, receiver, payer.getMoney());

        turnService.eliminate(state, payer);
        turnService.passTurn(state);
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
    private static boolean requireAccept(JsonNode payload) {
        JsonNode node = payload == null ? null : payload.get("isAccept");
        if(node == null || !node.isBoolean()) {
            throw new GameException(ErrorCode.INVALID_STATE);
        }
        return node.booleanValue();
    }

    /** payload에서 propertyIds를 읽는다. 없거나 배열이 아니거나 숫자가 아닌 값이 있으면 목록 오류로 거부한다. */
    private static List<Integer> requirePropertyIds(JsonNode payload) {
        JsonNode node = payload == null ? null : payload.get("propertyIds");
        if (node == null || !node.isArray()) {
            throw new GameException(ErrorCode.INVALID_PROPERTY_LIST);
        }
        List<Integer> propertyIds = new ArrayList<>();
        for (int i = 0; i < node.size(); ++i) {
            JsonNode element = node.get(i);
            if (!element.isNumber()) {
                throw new GameException(ErrorCode.INVALID_PROPERTY_LIST);
            }
            propertyIds.add(element.intValue());
        }
        return propertyIds;
    }

    /** payload에서 destinationPosition을 읽는다. 없거나 숫자가 아니면 올바르지 않은 칸으로 거부한다. */
    private static int requireDestination(JsonNode payload) {
        JsonNode node = payload == null ? null : payload.get("destinationPosition");
        if (node == null || !node.isNumber()) {
            throw new GameException(ErrorCode.INVALID_PROPERTY);
        }
        return node.intValue();
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
    private void requireStandingOn(PlayerState player, int propertyId) {
        TileData tile = tileAt(player.getPosition());
        if (!"PROPERTY".equals(tile.type()) || tile.propertyId() == null || tile.propertyId() != propertyId) {
            throw new GameException(ErrorCode.INVALID_PROPERTY);
        }
    }
    private TileData tileAt(int position) {
        return gameDataService.getTiles().get(position);
    }

}
