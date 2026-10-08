package com.sparta.ourmarbleserver.game.service;

import com.sparta.ourmarbleserver.card.dto.CardDrawnPayload;
import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.dto.*;
import com.sparta.ourmarbleserver.game.event.GameEndedEvent;
import com.sparta.ourmarbleserver.game.state.*;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.global.transport.EventPublisher;
import com.sparta.ourmarbleserver.property.domain.BuildingLevel;
import com.sparta.ourmarbleserver.property.dto.BuiltPayload;
import com.sparta.ourmarbleserver.property.dto.PropertiesSoldPayload;
import com.sparta.ourmarbleserver.property.dto.PropertyAcquiredPayload;
import com.sparta.ourmarbleserver.property.dto.PropertyPurchasedPayload;
import com.sparta.ourmarbleserver.property.service.PropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 게임 진행의 중심. 요청을 검증하고, 주사위·이동·턴 서비스를 순서대로 부르고, 상태를 저장하고, 알림을 보낸다.
 * (클라 RollDice → HandleDiceRolled → ProcessArrival 흐름)
 * 요청 type 분류와 요청 본문 읽기는 하지 않는다. MessageRouter가 type별로 아래 함수를 직접 호출한다.
 * 요청 처리 함수: rollDice, purchaseProperty, sellProperties, build, acquireProperty, chooseDestination, drawCard
 * 요청 처리 함수는 synchronized로 한 번에 하나씩 처리한다. (같은 요청이 겹치면 상태가 꼬이므로, startGame은 새 방이라 제외)
 * 요청은 roomId를 받지 않고, playerId로 그 플레이어가 속한 진행 중인 게임을 찾는다.
 */
@Service
@RequiredArgsConstructor
public class GameService {

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

    /**
     * 게임 종료 이벤트를 내보내는 곳. 로비가 이 이벤트를 받아 방을 지운다.
     * GameService가 LobbyService를 직접 알면 서로를 참조해서 서버가 뜨지 않으므로 이벤트로 알린다.
     * 생성자에 넣으면 테스트의 new GameService(...)가 모두 바뀌어서 setter로 받는다. 기본값은 아무것도 하지 않는다.
     */
    private ApplicationEventPublisher eventPublisher = event -> {};

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    // ===== 게임 시작 (로비가 부른다) =====

    /** 방 상태를 만들고 첫 플레이어의 턴을 시작한다. 닉네임은 빈 문자열이고 봇은 없는 시작이다. */
    public GameState startGame(String roomId, List<Long> playerIds) {
        return startGame(roomId, playerIds, Map.of(), Set.of());
    }

    /**
     * 방 상태를 만들고 첫 플레이어의 턴을 시작한다. playerIds의 순서가 턴 순서다.
     * nicknames에 없는 플레이어의 닉네임은 빈 문자열이고, botIds에 든 플레이어는 봇이다.
     * 플레이어 목록이 비었거나 중복이면 IllegalArgumentException.
     * 새 방의 상태를 만드는 것이라 다른 요청과 겹치지 않아 synchronized를 붙이지 않는다.
     */
    public GameState startGame(String roomId, List<Long> playerIds, Map<Long, String> nicknames, Set<Long> botIds) {
        if (playerIds.isEmpty() || new HashSet<>(playerIds).size() != playerIds.size()) {
            throw new IllegalArgumentException("플레이어 목록이 비었거나 중복이 있습니다." + playerIds);
        }
        GameState state = new GameState(roomId);
        for(long id : playerIds) {
            PlayerState player = new PlayerState(id);
            player.setMoney(START_MONEY);
            player.setNickname(nicknames.getOrDefault(id, ""));
            player.setBot(botIds.contains(id));
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
     * 무인도 영업정지 중이면 더블이 아닐 때 이동 없이 턴이 넘어가고, 3연속 더블이면 무인도로 간다.
     */
    public synchronized void rollDice(long playerId) {
        GameState state = validate(playerId, TurnPhase.AWAITING_ROLL);
        PlayerState player = state.getPlayerState(playerId);

        DiceService.DiceResult dice = diceService.roll(state);
        publish(state.getRoomId(), MessageType.DICE_ROLLED,
                new DiceRolledPayload(playerId, dice.dice1(), dice.dice2()));

        // 무인도 영업정지 중에 더블이 아니면 탈출 실패: 이동 없이 턴이 넘어간다.
        if (turnService.failIslandEscape(player, state.isDouble())) {
            turnService.passTurn(state);
            save(state);
            return;
        }
        turnService.escapeIslandByDouble(state, player);

        // 3연속 더블이면 이동하지 않고 무인도로 간다. (월급 없음, 도착 처리에서 영업정지 시작과 턴 넘김)
        OptionalInt island = turnService.findIslandPosition();
        if(state.getConsecutiveDoubleCount() >= 3 && island.isPresent()) {
            state.setDouble(false);
            state.setConsecutiveDoubleCount(0);
            moveService.moveDirectly(player, island.getAsInt());
            processArrival(state, player);
            save(state);
            return;
        }

        MoveService.MoveResult move = moveService.moveBy(player, dice.sum());
        if (move.passedStart()) {
            economyService.paySalary(player);
        }
        processArrival(state, player);

        save(state);
    }

    // ===== 땅 구매 / 거절 =====

    /**
     * 도착한 빈 땅을 사거나 거절한다. 어느 쪽이든 PROPERTY_PURCHASED를 방 전원에게 보내고 턴을 끝낸다.
     * 구매: 빈 땅 → 현금 확인 → 땅값 차감 → 주인 등록. 거절은 상태 변화 없이 isAccept=false로 알린다.
     */

    public synchronized void purchaseProperty(long playerId, int propertyId, boolean isAccept) {
        GameState state = validate(playerId, TurnPhase.AWAITING_PURCHASE, propertyId);
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

        publish(state.getRoomId(), MessageType.PROPERTY_PURCHASED,
                new PropertyPurchasedPayload(playerId, propertyId, isAccept));
        turnService.endTurn(state);

        save(state);
    }

    // ===== 땅 매각 =====

    /**
     * 통행료가 모자랄 때 땅을 팔아 현금을 채우고 통행료를 낸다. 매각 뒤에는 인수 선택 없이 턴을 끝낸다.
     * 검증 순서: 목록이 비었거나 중복(INVALID_PROPERTY_LIST) → 없는 땅(INVALID_PROPERTY) → 내 땅 아님(NOT_OWNER)
     * → 현금 + 매각가 합계 < 통행료(NOT_ENOUGH_SELL). 땅은 주인 없음 + 건설 단계 0으로 초기화된다.
     */
    public synchronized void sellProperties(long playerId, List<Integer> propertyIds) {
        GameState state = validate(playerId, TurnPhase.AWAITING_SELL);
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

        // 검증이 모두 끝났으니 상태를 바꾼다.
        selected.forEach(PropertyState::reset);
        economyService.deposit(payer, proceeds);
        economyService.transfer(payer, state.getPlayerState(tollProperty.getOwnerId()), toll);


        publish(state.getRoomId(), MessageType.PROPERTIES_SOLD, new PropertiesSoldPayload(playerId, propertyIds));
        turnService.endTurn(state);

        save(state);

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
    public synchronized void build(long playerId, int propertyId, boolean isAccept) {
        GameState state = validate(playerId, TurnPhase.AWAITING_BUILD, propertyId);
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

        publish(state.getRoomId(), MessageType.BUILT, new BuiltPayload(playerId, propertyId, isAccept));
        turnService.endTurn(state);

        save(state);
    }

    // ===== 인수 / 거절 =====

    /**
     * 통행료를 낸 뒤 남의 땅을 인수하거나 거절한다. 어느 쪽이든 PROPERTY_ACQUIRED를 방 전원에게 보내고 턴을 끝낸다.
     * 인수 검증: 남의 땅(CANNOT_ACQUIRE) → 현금 ≥ 인수가(NOT_ENOUGH_MONEY). 인수가는 이전 주인에게 이체하고
     * 건물 단계는 그대로 둔 채 주인만 바꾼다. 거절은 상태 변화 없이 isAccept=false로 알린다.
     */
    public synchronized void acquireProperty(long playerId, int propertyId, boolean isAccept) {
        GameState state = validate(playerId, TurnPhase.AWAITING_ACQUIRE, propertyId);
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

        publish(state.getRoomId(), MessageType.PROPERTY_ACQUIRED,
                new PropertyAcquiredPayload(playerId, propertyId, isAccept));
        turnService.endTurn(state);

        save(state);
    }

    // ===== 세계여행 =====

    /**
     * 세계여행 칸에서 시작한 턴에 목적지를 골라 월급 없이 이동하고, 도착한 칸을 처리한다.
     * DESTINATION_CHOSEN을 방 전원에게 보낸다. 목적지는 보드 안의 칸이면 어디든 고를 수 있다.
     */
    public synchronized void chooseDestination(long playerId, int destinationPosition) {
        GameState state = validate( playerId, TurnPhase.AWAITING_DESTINATION);
        PlayerState player = state.getPlayerState(playerId);

        if (destinationPosition < 0 || destinationPosition >= moveService.getTileCount()) {
            throw new GameException(ErrorCode.INVALID_PROPERTY);
        }

        publish(state.getRoomId(), MessageType.DESTINATION_CHOSEN,
                new DestinationChosenPayload(playerId, destinationPosition));

        moveService.moveDirectly(player, destinationPosition);
        processArrival(state, player);

        save(state);
    }

    // ===== 황금열쇠 =====

    /**
     * 황금열쇠 칸에서 카드를 한 장 뽑아 효과를 적용한다. CARD_DRAWN을 방 전원에게 보낸다.
     * 카드는 매번 전체에서 무작위로 고른다. (클라 DrawCard와 같음, 장수나 사용 여부는 보지 않는다)
     */
    public synchronized void drawCard(long playerId)
    {
        GameState state = validate(playerId, TurnPhase.AWAITING_DRAW_CARD);
        PlayerState player = state.getPlayerState(playerId);

        List<CardData> cards = gameDataService.getCards();
        if (cards.isEmpty()) {
            turnService.endTurn(state); // 카드가 없으면 턴이 멈추지 않도록 종료한다.
            save(state);
            return;
        }
        CardData card = cards.get(diceService.randomIndex(cards.size()));
        publish(state.getRoomId(), MessageType.CARD_DRAWN, new CardDrawnPayload(playerId, card.id()));

        applyCardEffect(state, player, card);

        save(state);
    }
    /** 카드 효과를 적용하고 다음 흐름(턴 종료 또는 도착 칸 처리)까지 진행한다. (클라 ExecuteCardEffect) */
    private void applyCardEffect(GameState state, PlayerState player, CardData card) {
        switch (card.effectType()) {
            case "Bonus" -> {
                economyService.deposit(player, card.amount());
                turnService.endTurn(state);
            }
            case "Penalty" -> {
                economyService.payPenalty(state, player, card.amount(), card.penaltyToFestivalPool());
                turnService.endTurn(state);
            }
            case "MoveTo" -> {
                MoveService.MoveResult move = moveService.moveTo(player, card.targetTileId());
                if (move.passedStart()) {
                    economyService.paySalary(player);
                }
                processArrival(state, player);
            }
            case "MoveBy" -> moveByCard(state, player, card.steps());
            case "GoToInspection" -> goToIsland(state, player);
            default -> turnService.endTurn(state); // 알 수 없는 효과는 턴이 멈추지 않도록 종료한다.
        }
    }

    /** N칸 이동 카드. 양수면 앞으로(출발 지점을 지나면 월급), 음수면 뒤로(월급 없음). */
    private void moveByCard(GameState state, PlayerState player, int steps) {
        if (steps > 0) {
            MoveService.MoveResult move = moveService.moveBy(player, steps);
            if (move.passedStart()) {
                economyService.paySalary(player);
            }
        } else {
            int size = moveService.getTileCount();
            moveService.moveDirectly(player, ((player.getPosition() + steps) % size + size) % size);
        }
        processArrival(state, player);
    }

    /** 무인도 카드. 월급 없이 무인도로 가고, 도착 처리에서 영업정지가 시작되고 턴이 넘어간다. */
    private void goToIsland(GameState state, PlayerState player) {
        OptionalInt island = turnService.findIslandPosition();
        if (island.isEmpty()) {
            turnService.endTurn(state);
            return;
        }
        moveService.moveDirectly(player, island.getAsInt());
        processArrival(state, player);
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
            case "ISLAND" -> {
                turnService.imprison(player);
                turnService.passTurn(state); // 강제로 턴을 넘긴다.
            }
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

    // ===== 게임 종료 =====

    /**
     * 요청 처리의 마지막에 상태를 저장한다. 게임이 끝났으면 생존자의 최종 등수를 정하고 종료 이벤트를 한 번 내보낸다.
     * (등수가 아직 안 정해진 생존자가 있을 때만 정하므로, 이벤트도 한 번만 나간다)
     */
    private void save(GameState state) {
        boolean justEnded = state.isGameOver() && assignSurvivorRanks(state);
        repository.save(state);
        if (justEnded) {
            eventPublisher.publishEvent(new GameEndedEvent(state.getRoomId()));
        }
    }

    /**
     * 생존자에게 최종 등수를 1위부터 부여한다. (클라 AssignSurvivorRanks와 같은 규칙)
     * 총자산(현금 + 투자금) 내림차순 → 현금 내림차순 → 원래 턴 순서. 파산한 플레이어의 등수는 이미 정해져 있어 건드리지 않는다.
     * 새로 부여했으면 true, 이미 정해져 있었으면 false.
     */
    private boolean assignSurvivorRanks(GameState state) {
        List<PlayerState> survivors = state.players().stream()
                .filter(player -> !player.isBankrupt())
                .toList();
        if (survivors.stream().noneMatch(player -> player.getFinalRank() == 0)) {
            return false;
        }

        List<PlayerState> ranked = new ArrayList<>(survivors);
        ranked.sort(Comparator
                .comparingLong((PlayerState player) -> propertyService.getTotalAsset(state, player.getPlayerId())).reversed()
                .thenComparing(Comparator.comparingLong(PlayerState::getMoney).reversed())
                .thenComparingInt(player -> state.playerOrder().indexOf(player.getPlayerId())));

        int rank = 1;
        for (PlayerState player : ranked) {
            player.setFinalRank(rank++);
        }
        return true;
    }

    // ===== 공통 검증 =====

    private GameState validate(long playerId, TurnPhase requiredPhase) {
        return validate(playerId, requiredPhase, null);
    }

    /**
     * 모든 요청의 공통 검증: 플레이어가 속한 진행 중인 게임 존재 → 땅 존재 → 내 차례 → 파산 여부
     * → 요청이 현재 phase에 맞는지. 상태를 바꾸기 전에 호출하므로 실패해도 상태는 그대로다.
     */
    private GameState validate(long playerId, TurnPhase requiredPhase, Integer propertyId) {
        GameState state = repository.findByPlayerId(playerId)
                .orElseThrow(() -> new GameException(ErrorCode.INVALID_STATE));
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

    /**
     * 방 전원에게 알림을 보낸다.
     * TODO(네트워크): 실제 EventPublisher가 확정되기 전까지 알림이 나가지 않도록 호출을 주석 처리했다.
     * 확정되면 아래 줄의 주석을 풀고, EventPublisher 모양이 바뀌었으면 여기만 고친다.
     */
    private void publish(String roomId, MessageType type, Object payload) {
        //publisher.publishToRoom(roomId,type, payload);
    }
}
