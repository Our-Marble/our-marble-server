package com.sparta.ourmarbleserver.game.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/** 방 하나의 게임 진행 상태. */
@Getter
@Setter
@RequiredArgsConstructor
public class GameState {

    private final String roomId;

    /** 지금까지 진행된 차례 수 (플레이어 한 명의 차례마다 1 증가). */
    private int turnNumber;

    /** 현재 라운드. 살아 있는 플레이어가 모두 한 번씩 하면 1 증가. */
    private int roundNumber;

    private long currentPlayerId;

    /** 국세청에 쌓인 적립금. */
    private long welfareFund;

    private TurnPhase phase = TurnPhase.AWAITING_ROLL;

    /** 이번 굴림이 더블인지. 더블이면 같은 플레이어가 한 번 더 굴린다. */
    private boolean isDouble;

    /** 연속 더블 횟수. 3이 되면 이동 없이 무인도로 간다. */
    private int consecutiveDoubleCount;

    private boolean isGameOver;

    /** 턴 순서. 파산한 플레이어를 건너뛰고 다음 플레이어를 찾을 때 쓴다. */
    @Getter(AccessLevel.NONE)
    private final List<Long> playerOrder = new ArrayList<>();

    @Getter(AccessLevel.NONE)
    private final List<PlayerState> players = new ArrayList<>();

    @Getter(AccessLevel.NONE)
    private final List<PropertyState> properties = new ArrayList<>();

    // ---- 목록은 바깥에서 마음대로 고치지 못하게 읽기 전용으로 돌려준다 ----

    public List<Long> playerOrder() {
        return Collections.unmodifiableList(playerOrder);
    }

    public void setPlayerOrder(List<Long> order) {
        playerOrder.clear();
        playerOrder.addAll(order);
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

    /** 새 턴이 시작될 때 더블 상태를 되돌린다. */
    public void resetDoubleState() {
        this.isDouble = false;
        this.consecutiveDoubleCount = 0;
    }

    /** 방에 있는 플레이어 상태. 없으면 호출한 쪽의 버그이므로 예외를 던진다. (클라 GetPlayerState) */
    public PlayerState getPlayerState(long playerId) {
        for (PlayerState player : players) {
            if (player.getPlayerId() == playerId) {
                return player;
            }
        }
        throw new IllegalArgumentException("방에 없는 플레이어: " + playerId);
    }

    public PlayerState currentPlayer() {
        return getPlayerState(currentPlayerId);
    }

    /** 요청의 땅 번호는 틀릴 수 있으므로 Optional로 돌려준다. (없으면 INVALID_PROPERTY) (클라 GetPropertyState) */
    public Optional<PropertyState> getPropertyState(int propertyId) {
        for (PropertyState property : properties) {
            if (property.getPropertyId() == propertyId) {
                return Optional.of(property);
            }
        }
        return Optional.empty();
    }
}