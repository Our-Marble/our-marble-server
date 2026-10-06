package com.sparta.ourmarbleserver.game.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** 방 하나의 게임 진행 상태. */
public class GameState {

    private final String gameId;

    /** 지금까지 진행된 차례 수 (플레이어 한 명의 차례마다 1 증가). */
    private int turnNumber;

    /** 현재 라운드. 살아 있는 플레이어가 모두 한 번씩 하면 1 증가. */
    private int roundNumber;

    private long currentPlayerId;

    /** 국세청에 쌓인 적립금. */
    private long welfareFund;

    private TurnPhase phase = TurnPhase.MOVING;

    /** 턴 순서대로 담는다. */
    private final List<PlayerState> players = new ArrayList<>();
    private final List<PropertyState> properties = new ArrayList<>();

    public GameState(String gameId) {
        this.gameId = gameId;
    }

    public String gameId() {
        return gameId;
    }

    public int turnNumber() {
        return turnNumber;
    }

    public void setTurnNumber(int turnNumber) {
        this.turnNumber = turnNumber;
    }

    public int roundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public long currentPlayerId() {
        return currentPlayerId;
    }

    public void setCurrentPlayerId(long currentPlayerId) {
        this.currentPlayerId = currentPlayerId;
    }

    public long welfareFund() {
        return welfareFund;
    }

    public void setWelfareFund(long welfareFund) {
        this.welfareFund = welfareFund;
    }

    public TurnPhase phase() {
        return phase;
    }

    public void setPhase(TurnPhase phase) {
        this.phase = phase;
    }

    public List<PlayerState> players() {
        return Collections.unmodifiableList(players);
    }

    public List<PropertyState> properties() {
        return Collections.unmodifiableList(properties);
    }

    public void addPlayer(PlayerState player) {
        players.add(player);
    }

    public void addProperty(PropertyState property) {
        properties.add(property);
    }

    /** 방에 있는 플레이어. 없으면 호출한 쪽의 버그이므로 예외를 던진다. */
    public PlayerState player(long playerId) {
        for (PlayerState player : players) {
            if (player.playerId() == playerId) {
                return player;
            }
        }
        throw new IllegalArgumentException("방에 없는 플레이어: " + playerId);
    }

    public PlayerState currentPlayer() {
        return player(currentPlayerId);
    }

    /** 요청의 땅 번호는 틀릴 수 있으므로 Optional로 돌려준다. (없으면 INVALID_PROPERTY) */
    public Optional<PropertyState> findProperty(int propertyId) {
        for (PropertyState property : properties) {
            if (property.propertyId() == propertyId) {
                return Optional.of(property);
            }
        }
        return Optional.empty();
    }
}