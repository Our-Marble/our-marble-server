package com.sparta.ourmarbleserver.game.service;

import com.sparta.ourmarbleserver.card.dto.CardDrawnPayload;
import com.sparta.ourmarbleserver.economy.service.EconomyService;
import com.sparta.ourmarbleserver.game.dto.*;
import com.sparta.ourmarbleserver.game.event.GameEndedEvent;
import com.sparta.ourmarbleserver.game.state.*;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.GameErrorCode;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.property.dto.BuiltPayload;
import com.sparta.ourmarbleserver.property.dto.PropertiesSoldPayload;
import com.sparta.ourmarbleserver.property.dto.PropertyAcquiredPayload;
import com.sparta.ourmarbleserver.property.dto.PropertyPurchasedPayload;
import com.sparta.ourmarbleserver.property.service.PropertyService;
import com.sparta.ourmarbleserver.scheduler.service.TimerService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 게임 진행의 중심. 요청을 검증하고, 주사위·이동·턴 서비스를 순서대로 부르고, 상태를 저장하고, 보낼 알림을 GameResult로 돌려준다.
 * 알림을 실제로 보내는 일은 요청을 부른 쪽(MessageRouter)이 한다. (서비스는 웹소켓과 전송 방식을 모른다)
 * (클라 RollDice → HandleDiceRolled → ProcessArrival 흐름)
 * 요청 type 분류와 요청 본문 읽기는 하지 않는다. MessageRouter가 type별로 아래 함수를 직접 호출한다.
 * 요청 처리 함수: rollDice, purchaseProperty, sellProperties, build, acquireProperty, chooseDestination, drawCard
 * 요청 처리 함수는 synchronized로 한 번에 하나씩 처리한다. (같은 요청이 겹치면 상태가 꼬이므로, startGame은 새 방이라 제외)
 * 요청은 roomId를 받지 않고, playerId로 그 플레이어가 속한 진행 중인 게임을 찾는다.
 * 도착 칸 처리(통행료 정산, 파산)는 ArrivalService가 하고, 이 클래스를 거쳐서만 불린다.
 * 카드 효과 적용은 CardService가 하고, 이 클래스를 거쳐서만 불린다.
 * 땅 거래 규칙(구매, 건설, 인수, 매각)은 PropertyTradeService가 하고, 이 클래스를 거쳐서만 불린다.
 */
@Service
@RequiredArgsConstructor
public class GameService {

    /**
     * 초기 자금
     */
    public static final long START_MONEY = GameConfig.DEFAULT_START_MONEY;
    private final GameStateRepository repository;
    private final DiceService diceService;
    private final MoveService moveService;
    private final TurnService turnService;
    private final EconomyService economyService;
    private final PropertyService propertyService;
    private final GameDataService gameDataService;
    private final ArrivalService arrivalService;
    private final CardService cardService;
    private final PropertyTradeService tradeService;

    /**
     * 봇 차례는 사람처럼 제한 시간을 기다리지 않고 이 시간(초) 뒤에 바로 진행한다.
     */
    private static final int BOT_DELAY_SECONDS = 3;

    /**
     * 게임 종료 이벤트를 내보내는 곳. 이 서비스가 상태를 지우고, 로비가 방을 WAITING으로 되돌린다.
     * GameService가 LobbyService를 직접 알면 서로를 참조해서 서버가 뜨지 않으므로 이벤트로 알린다.
     * 생성자에 넣으면 테스트의 new GameService(...)가 모두 바뀌어서 setter로 받는다. 기본값은 아무것도 하지 않는다.
     */
    private ApplicationEventPublisher eventPublisher = event -> {
    };

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 타이머. 상태를 저장하는 때마다(startGame, save) 그 방의 타이머를 새로 시작하고, 게임이 끝나면 지운다.
     * 생성자에 넣으면 테스트의 new GameService(...)가 모두 바뀌어서 setter로 받는다. 테스트에서는 넣지 않으면 타이머 없이 동작한다.
     */
    private TimerService timerService;

    @Autowired
    public void setTimerService(TimerService timerService) {
        this.timerService = timerService;
    }

    // ===== 게임 시작 (로비가 부른다) =====

    /**
     * 방 상태를 만들고 첫 플레이어의 턴을 시작한다. 닉네임은 빈 문자열이고 봇은 없는 시작이다.
     */
    public GameState startGame(String roomId, List<Long> playerIds) {
        return startGame(roomId, playerIds, Map.of(), Set.of());
    }

    /**
     * 방 상태를 만들고 첫 플레이어의 턴을 시작한다. playerIds의 순서가 턴 순서다.
     * 기본 설정(GameConfig.defaults())으로 시작한다.
     */
    public GameState startGame(String roomId, List<Long> playerIds, Map<Long, String> nicknames, Set<Long> botIds) {
        return startGame(roomId, playerIds, nicknames, botIds, GameConfig.defaults());
    }

    /**
     * 방 상태를 만들고 첫 플레이어의 턴을 시작한다. playerIds의 순서가 턴 순서다.
     * config의 초기 자금, 월급, 세금, 최대 라운드, 무인도 영업정지 턴으로 진행한다. (방장이 정한 설정)
     * nicknames에 없는 플레이어의 닉네임은 빈 문자열이고, botIds에 든 플레이어는 봇이다.
     * 플레이어 목록이 비었거나 중복이거나 2~4명이 아니면 IllegalArgumentException,
     * 같은 방 번호로 진행 중인 게임이 이미 있으면 IllegalStateException.
     * synchronized를 붙이지 않는다. 요청 처리 함수는 서비스 락을 잡은 채 save()에서 종료 이벤트를 내보내고,
     * 그 이벤트를 받는 LobbyService.onGameEnded는 로비 락을 기다린다. 반대로 LobbyService.start는 로비 락을 잡은 채
     * 이 함수를 부른다. 여기에 서비스 락을 걸면 두 락이 서로를 기다려 교착이 생긴다.
     * (같은 방 번호의 동시 시작은 로비의 start가 이미 직렬화한다)
     */
    public GameState startGame(String roomId, List<Long> playerIds, Map<Long, String> nicknames, Set<Long> botIds, GameConfig config) {
        if (playerIds.isEmpty() || new HashSet<>(playerIds).size() != playerIds.size()) {
            throw new IllegalArgumentException("플레이어 목록이 비었거나 중복이 있습니다." + playerIds);
        }
        if (playerIds.size() < GameConfig.MIN_PLAYERS || playerIds.size() > GameConfig.MAX_PLAYERS) {
            throw new IllegalArgumentException("플레이어는 " + GameConfig.MIN_PLAYERS + "~" + GameConfig.MAX_PLAYERS + "명이어야 합니다: " + playerIds.size());
        }
        boolean running = repository.findById(roomId).filter(existing -> !existing.isGameOver()).isPresent();
        if (running) {
            throw new IllegalStateException("이미 진행 중인 게임이 있는 방입니다: " + roomId);
        }

        GameState state = new GameState(roomId);
        state.setConfig(Objects.requireNonNull(config));
        for (long id : playerIds) {
            PlayerState player = new PlayerState(id);
            player.setMoney(config.startMoney());
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
        restartTimer(state);
        return state;
    }

    // ===== 주사위 굴리기 =====

    /**
     * 주사위를 굴리고 이동한 뒤 도착 칸에 따라 다음 phase를 정한다.
     * 알림은 DICE_ROLLED 하나만 나간다. 이동과 월급은 클라가 같은 규칙으로 계산한다.
     * 무인도 영업정지 중이면 더블이 아닐 때 이동 없이 턴이 넘어가고, 3연속 더블이면 무인도로 간다.
     */
    public synchronized GameResult rollDice(long playerId) {
        GameState state = validate(playerId, TurnPhase.AWAITING_ROLL);
        PlayerState player = state.getPlayerState(playerId);

        DiceService.DiceResult dice = diceService.roll(state);
        GameResult result = GameResult.of(state.getRoomId(), MessageType.DICE_ROLLED, new DiceRolledPayload(playerId, dice.dice1(), dice.dice2()));

        // 무인도 영업정지 중에 더블이 아니면 탈출 실패: 이동 없이 턴이 넘어간다.
        if (turnService.failIslandEscape(player, state.isDouble())) {
            turnService.passTurn(state);
            save(state);
            return result;
        }
        turnService.escapeIslandByDouble(state, player);

        // 3연속 더블이면 이동하지 않고 무인도로 간다. (월급 없음, 도착 처리에서 영업정지 시작과 턴 넘김)
        OptionalInt island = turnService.findIslandPosition();
        if (state.getConsecutiveDoubleCount() >= 3 && island.isPresent()) {
            state.setDouble(false);
            state.setConsecutiveDoubleCount(0);
            moveService.moveDirectly(player, island.getAsInt());
            arrivalService.processArrival(state, player);
            save(state);
            return result;
        }

        MoveService.MoveResult move = moveService.moveBy(player, dice.sum());
        if (move.passedStart()) {
            economyService.paySalary(player, state.getConfig().salaryAmount());
        }
        arrivalService.processArrival(state, player);

        save(state);
        return result;
    }

    // ===== 땅 구매 / 거절 =====

    /**
     * 도착한 빈 땅을 사거나 거절한다. 어느 쪽이든 PROPERTY_PURCHASED를 방 전원에게 보내고 턴을 끝낸다.
     * 구매: 빈 땅 → 현금 확인 → 땅값 차감 → 주인 등록. 거절은 상태 변화 없이 isAccept=false로 알린다.
     */

    public synchronized GameResult purchaseProperty(long playerId, int propertyId, boolean isAccept) {
        GameState state = validate(playerId, TurnPhase.AWAITING_PURCHASE, propertyId);
        PlayerState player = state.getPlayerState(playerId);
        tradeService.purchase(state, player, propertyId, isAccept);

        GameResult result = GameResult.of(state.getRoomId(), MessageType.PROPERTY_PURCHASED, new PropertyPurchasedPayload(playerId, propertyId, isAccept));
        turnService.endTurn(state);

        save(state);
        return result;
    }

    // ===== 땅 매각 =====

    /**
     * 통행료가 모자랄 때 땅을 팔아 현금을 채우고 통행료를 낸다. 매각 뒤에는 인수 선택 없이 턴을 끝낸다.
     * 검증 순서: 목록이 null이거나 비었거나 null 값이 있거나 중복(INVALID_PROPERTY_LIST) → 없는 땅(INVALID_PROPERTY) → 내 땅 아님(NOT_OWNER)
     * → 현금 + 매각가 합계 < 통행료(NOT_ENOUGH_SELL). 땅은 주인 없음 + 건설 단계 0으로 초기화된다.
     */
    public synchronized GameResult sellProperties(long playerId, List<Integer> propertyIds) {
        GameState state = validate(playerId, TurnPhase.AWAITING_SELL);
        PlayerState payer = state.getPlayerState(playerId);

        tradeService.sell(state, payer, propertyIds);

        GameResult result = GameResult.of(state.getRoomId(), MessageType.PROPERTIES_SOLD, new PropertiesSoldPayload(playerId, propertyIds));
        turnService.endTurn(state);

        save(state);
        return result;
    }

    // ===== 건설 / 거절 =====

    /**
     * 내 땅에 도착했을 때 한 단계 건설하거나 거절한다. 어느 쪽이든 BUILT를 방 전원에게 보내고 턴을 끝낸다.
     * 건설 검증: 내 땅(NOT_OWNER) → 건설 가능 땅(CANNOT_BUILD) → 호텔 아님(MAX_LEVEL) → 현금 ≥ 건설비(NOT_ENOUGH_MONEY).
     * 건물은 한 단계씩 올라가고 건설비는 올라갈 단계의 비용이다. 거절은 상태 변화 없이 isAccept=false로 알린다.
     */
    public synchronized GameResult build(long playerId, int propertyId, boolean isAccept) {
        GameState state = validate(playerId, TurnPhase.AWAITING_BUILD, propertyId);
        PlayerState player = state.getPlayerState(playerId);

        tradeService.build(state, player, propertyId, isAccept);

        GameResult result = GameResult.of(state.getRoomId(), MessageType.BUILT, new BuiltPayload(playerId, propertyId, isAccept));
        turnService.endTurn(state);

        save(state);
        return result;
    }

    // ===== 인수 / 거절 =====

    /**
     * 통행료를 낸 뒤 남의 땅을 인수하거나 거절한다. 어느 쪽이든 PROPERTY_ACQUIRED를 방 전원에게 보내고 턴을 끝낸다.
     * 인수 검증: 남의 땅(CANNOT_ACQUIRE) → 현금 ≥ 인수가(NOT_ENOUGH_MONEY). 인수가는 이전 주인에게 이체하고
     * 건물 단계는 그대로 둔 채 주인만 바꾼다. 거절은 상태 변화 없이 isAccept=false로 알린다.
     */
    public synchronized GameResult acquireProperty(long playerId, int propertyId, boolean isAccept) {
        GameState state = validate(playerId, TurnPhase.AWAITING_ACQUIRE, propertyId);
        PlayerState player = state.getPlayerState(playerId);

        tradeService.acquire(state, player, propertyId, isAccept);

        GameResult result = GameResult.of(state.getRoomId(), MessageType.PROPERTY_ACQUIRED, new PropertyAcquiredPayload(playerId, propertyId, isAccept));
        turnService.endTurn(state);

        save(state);
        return result;
    }

    // ===== 세계여행 =====

    /**
     * 세계여행 칸에서 시작한 턴에 목적지를 골라 이동하고(출발 지점을 지나면 월급), 도착한 칸을 처리한다.
     * DESTINATION_CHOSEN을 방 전원에게 보낸다. 목적지는 보드 안의 칸이면 세계여행 칸을 뺀 어디든 고를 수 있다.
     */
    public synchronized GameResult chooseDestination(long playerId, int destinationPosition) {
        GameState state = validate(playerId, TurnPhase.AWAITING_DESTINATION);
        PlayerState player = state.getPlayerState(playerId);

        if (destinationPosition < 0 || destinationPosition >= moveService.getTileCount()) {
            throw new GameException(GameErrorCode.INVALID_PROPERTY);
        }

        if ("WORLD_TRAVEL".equals(tileAt(destinationPosition).type())) {
            throw new GameException(GameErrorCode.INVALID_PROPERTY); //세계여행 캰은 목적지로 고를 수 없다.
        }

        GameResult result = GameResult.of(state.getRoomId(), MessageType.DESTINATION_CHOSEN, new DestinationChosenPayload(playerId, destinationPosition));

        MoveService.MoveResult move = moveService.moveTo(player, destinationPosition);
        if (move.passedStart()) {
            economyService.paySalary(player, state.getConfig().salaryAmount());   // 출발 지점을 지나면 월급
        }
        arrivalService.processArrival(state, player);

        save(state);
        return result;
    }

    // ===== 황금열쇠 =====

    /**
     * 황금열쇠 칸에서 카드를 한 장 뽑아 효과를 적용한다. CARD_DRAWN을 방 전원에게 보낸다.
     * 카드는 매번 전체에서 무작위로 고른다. (클라 DrawCard와 같음, 장수나 사용 여부는 보지 않는다)
     */
    public synchronized GameResult drawCard(long playerId) {
        GameState state = validate(playerId, TurnPhase.AWAITING_DRAW_CARD);
        PlayerState player = state.getPlayerState(playerId);

        List<CardData> cards = gameDataService.getCards();
        if (cards.isEmpty()) {
            turnService.endTurn(state); // 카드가 없으면 턴이 멈추지 않도록 종료한다.
            save(state);
            return GameResult.none(state.getRoomId());
        }
        CardData card = cards.get(diceService.randomIndex(cards.size()));
        GameResult result = GameResult.of(state.getRoomId(), MessageType.CARD_DRAWN, new CardDrawnPayload(playerId, card.id()));

        cardService.applyCardEffect(state, player, card);

        save(state);
        return result;
    }

    // ===== 상태 동기화 (재접속) =====

    /**
     * 플레이어가 속한 진행 중인 게임의 현재 상태 전체를 GAME_STATE_SYNC 알림으로 만든다. (재접속 때 화면 복구용)
     * 상태를 바꾸지 않는다. 요청 처리와 같은 락을 잡아서, 요청이 처리되는 도중의 상태를 읽지 않는다.
     * 속한 진행 중인 게임이 없으면(로비에 있는 사람 등) 빈 값을 돌려준다.
     */
    public synchronized Optional<GameMessage> syncState(long playerId) {
        return repository.findByPlayerId(playerId).map(state -> {
            if (timerService != null) {
                timerService.release(state.getRoomId(), playerId);   // 재접속한 사람은 AFK를 푼다
            }
            return new GameMessage(MessageType.GAME_STATE_SYNC, GameStateSyncPayload.from(state));
        });
    }

    // ===== 상태 저장과 게임 종료 =====

    /**
     * 요청 처리의 마지막에 상태를 저장하고, 타이머를 새로 시작한다. (게임이 끝났으면 타이머를 지운다)
     * 게임이 끝났으면 생존자의 최종 등수를 정하고 종료 이벤트를 한 번 내보낸다.
     * (등수가 아직 안 정해진 생존자가 있을 때만 정하므로, 이벤트도 한 번만 나간다)
     */
    private void save(GameState state) {
        boolean justEnded = state.isGameOver() && assignSurvivorRanks(state);
        repository.save(state);
        restartTimer(state);
        if (justEnded) {
            eventPublisher.publishEvent(GameEndedEvent.of(state));
        }
    }

    /**
     * 상태가 저장된 시점에 타이머를 새로 시작한다. (이 시점에 다음에 기다릴 phase가 정해져 있다)
     * 게임이 끝났으면 타이머를 지운다. 타이머는 예약만 하고 이 서비스를 다시 부르지 않는다.
     */
    private void restartTimer(GameState state) {
        if (timerService == null) {
            return;
        }
        if (state.isGameOver()) {
            timerService.cancel(state.getRoomId());
            return;
        }
        if (state.currentPlayer().isBot()) {
            timerService.startBot(state.getRoomId(), state.getCurrentPlayerId(), state.getTurnNumber(),
                    state.getPhase(), BOT_DELAY_SECONDS);
            return;
        }
        timerService.start(state.getRoomId(), state.getCurrentPlayerId(), state.getTurnNumber(),
                state.getPhase(), state.getConfig().limitTimeSeconds());
    }

    /**
     * 게임이 끝나면(GameEndedEvent) 그 방의 게임 상태를 지운다. 결과 화면은 클라가 처리하므로 서버는 상태를 남기지 않는다.
     * save()가 요청 처리 락을 잡은 채 이벤트를 내보내서 이 함수도 같은 스레드에서 불린다. 락은 따로 걸지 않는다.
     * (방은 로비가 WAITING으로 되돌린다)
     */
    @EventListener
    public void onGameEnded(GameEndedEvent event) {
        repository.deleteById(event.roomId());
    }

    /**
     * 생존자에게 최종 등수를 1위부터 부여한다. (클라 AssignSurvivorRanks와 같은 규칙)
     * 총자산(현금 + 투자금) 내림차순 → 현금 내림차순 → 원래 턴 순서. 파산한 플레이어의 등수는 이미 정해져 있어 건드리지 않는다.
     * 새로 부여했으면 true, 이미 정해져 있었으면 false.
     */
    private boolean assignSurvivorRanks(GameState state) {
        List<PlayerState> survivors = state.players().stream().filter(player -> !player.isBankrupt()).toList();
        if (survivors.stream().noneMatch(player -> player.getFinalRank() == 0)) {
            return false;
        }

        List<PlayerState> ranked = new ArrayList<>(survivors);
        ranked.sort(Comparator.comparingLong((PlayerState player) -> propertyService.getTotalAsset(state, player.getPlayerId())).reversed().thenComparing(Comparator.comparingLong(PlayerState::getMoney).reversed()).thenComparingInt(player -> state.playerOrder().indexOf(player.getPlayerId())));

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
        GameState state = repository.findByPlayerId(playerId).orElseThrow(() -> new GameException(GameErrorCode.INVALID_STATE));
        if (timerService != null) {
            // 유저 우선: 사람이 보낸 요청은 거부되더라도 AFK를 풀고, 서버의 대신 진행은 AFK(또는 봇)인 플레이어에게만 실행한다.
            timerService.release(state.getRoomId(), playerId);
            if (!timerService.canAutoPlay(state.getRoomId(), playerId, state.getPlayerState(playerId).isBot())) {
                throw new GameException(GameErrorCode.INVALID_STATE);
            }
        }
        if (propertyId != null && state.getPropertyState(propertyId).isEmpty()) {
            throw new GameException(GameErrorCode.INVALID_PROPERTY);
        }
        if (state.getCurrentPlayerId() != playerId) {
            throw new GameException(GameErrorCode.NOT_YOUR_TURN);
        }
        if (state.getPlayerState(playerId).isBankrupt()) {
            throw new GameException(GameErrorCode.PLAYER_BANKRUPT);
        }
        if (state.getPhase() != requiredPhase) {
            throw new GameException(GameErrorCode.INVALID_STATE);
        }
        return state;
    }

    private TileData tileAt(int position) {
        return gameDataService.getTiles().get(position);
    }
}