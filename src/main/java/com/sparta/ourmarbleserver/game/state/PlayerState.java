package com.sparta.ourmarbleserver.game.state;

import java.util.ArrayList;
import java.util.List;

/** 플레이어 한 명의 게임 중 상태. */
public class PlayerState {

    private final long playerId;
    private int position;
    private long money;

    /** 무인도 탈출까지 남은 턴 수. */
    private int islandTurnsRemaining;

    /** 황금열쇠에서 얻은 사용 카드. */
    private final List<Integer> cardIds = new ArrayList<>();

    private boolean bankrupt;

    /**
     * 최종 등수. 0이면 아직 정해지지 않음.
     * - 파산 시: 그 시점에 남아 있던 인원 수 (먼저 파산할수록 낮은 등수)
     * - 게임 종료 시: 살아남은 플레이어에게 1위부터 부여
     */
    private int finalRank;

    public PlayerState(long playerId) {
        this.playerId = playerId;
    }

    public long playerId() {
        return playerId;
    }

    public int position() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public long money() {
        return money;
    }

    public void setMoney(long money) {
        this.money = money;
    }

    /** 현금을 delta만큼 더한다. 줄이려면 음수를 넘긴다. */
    public void addMoney(long delta) {
        this.money += delta;
    }

    public int islandTurnsRemaining() {
        return islandTurnsRemaining;
    }

    public void setIslandTurnsRemaining(int islandTurnsRemaining) {
        this.islandTurnsRemaining = islandTurnsRemaining;
    }

    public List<Integer> cardIds() {
        return cardIds;
    }

    public boolean isBankrupt() {
        return bankrupt;
    }

    public void setBankrupt(boolean bankrupt) {
        this.bankrupt = bankrupt;
    }

    public int finalRank() {
        return finalRank;
    }

    public void setFinalRank(int finalRank) {
        this.finalRank = finalRank;
    }
}