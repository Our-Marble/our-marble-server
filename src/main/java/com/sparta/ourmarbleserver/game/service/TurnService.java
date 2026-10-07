package com.sparta.ourmarbleserver.game.service;

import com.sparta.ourmarbleserver.game.dto.TileData;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.PlayerState;
import com.sparta.ourmarbleserver.game.state.TurnPhase;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 턴 전환을 맡는다. (클라 HandleTurnChanged / ProcessEndTurn / GetNextPlayerId / GetNextRound)
 * 상태만 바꾸고, 알림 전송과 저장은 GameService가 한다. 게임이 끝나면 state.isGameOver()가 true가 된다.
 */
@Service
public class TurnService {
    /** 최대 라운드. 이 라운드를 넘기면 게임이 끝난다. */
    public static final int MAX_ROUND = 30;
    private static final String WORLD_TRAVEL = "WORLD_TRAVEL";

    private final List<TileData> tiles;

    public TurnService(GameDataService gameDataService) {
        this.tiles = gameDataService.getTiles();
    }

    /** 턴 종료. 더블이면 같은 플레이어가 다시 굴리고, 아니면 다음 플레이어로 넘어간다. (클라 ProcessEndTurn) */
    public void endTurn(GameState state) {
        if(state.isDouble()) {
            state.setPhase(TurnPhase.AWAITING_ROLL);
            return;
        }
        passTurn(state);
    }

    /** 더블과 상관없이 다음 플레이어로 턴을 넘긴다. (무인도, 세계여행 도착 등 강제로 넘길 때) */
    public void passTurn(GameState state) {
        startTurn(state,getNextPlayerId(state));
    }

    /**
     * playerId의 턴을 시작한다. (게임 시작 때도 첫 플레이어로 호출)
     * 게임 종료 조건(1명만 남음, 최대 라운드 초과)이면 턴을 바꾸지 않고 게임 종료로 표시한다.
     */

    public void startTurn(GameState state, long playerId) {
        if (state.isGameOver()) {
            return;
        }
        if (isGameOver(state)) {
            state.setGameOver(true);
            return;
        }
        int nextRound = getNextRound(state, playerId);
        if(nextRound > MAX_ROUND) {
            state.setGameOver(true);
            return;
        }

        state.setCurrentPlayerId(playerId);
        state.setTurnNumber(state.getTurnNumber() + 1);
        state.setRoundNumber(nextRound);
        state.resetDoubleState();
        state.setPhase(isOnWorldTravel(state.getPlayerState(playerId))
                ? TurnPhase.AWAITING_DESTINATION
                : TurnPhase.AWAITING_ROLL);
    }


    /** 현재 플레이어 다음 순서부터 한 바퀴 돌며 파산하지 않은 플레이어를 찾는다. 없으면 현재 플레이어. */
    public long getNextPlayerId(GameState state) {
        List<Long> order = state.playerOrder();
        int currentIndex = order.indexOf(state.getCurrentPlayerId());

        for (int i=1; i <= order.size(); i++) {
            long candidateId = order.get((currentIndex + i) % order.size());
            if (!state.getPlayerState(candidateId).isBankrupt()) {
                return candidateId;
            }
        }
        return state.getCurrentPlayerId();
    }

    /** 살아 있는 플레이어가 1명 이하면 게임 종료 */
    public boolean isGameOver(GameState state) {
        long alive = state.players().stream().filter(p -> !p.isBankrupt()).count();
        return alive <= 1;
    }

    /**
     * 플레이어를 파산 처리한다. 최종 등수는 파산하는 시점의 생존자 수다. (4명 중 첫 파산 → 4위)
     * 살아 있는 플레이어가 1명이면 게임 종료로 표시한다. 이미 파산한 플레이어는 다시 처리하지 않는다.
     */
    public void eliminate(GameState state, PlayerState player) {
        if (player.isBankrupt()) {
            return;
        }
        long alive = state.players().stream().filter(p -> !p.isBankrupt()).count();
        player.setFinalRank((int)alive);
        player.setBankrupt(true);

        if (isGameOver(state)) {
            state.setGameOver(true);
        }
    }

    /** 다음 차례가 몇 라운드인지. 턴 순서가 처음으로 돌아오면 1 늘어난다. (첫 턴은 1라운드) */
    int getNextRound(GameState state, long nextPlayerId) {
        if (state.getRoundNumber() == 0) {
            return 1;
        }
        List<Long> order = state.playerOrder();
        int prevIndex = order.indexOf(state.getCurrentPlayerId());
        int nextIndex = order.indexOf(nextPlayerId);
        return nextIndex <= prevIndex ? state.getRoundNumber() +1 : state.getRoundNumber();
    }

    private boolean isOnWorldTravel(PlayerState player) {
        return WORLD_TRAVEL.equals(tiles.get(player.getPosition()).type());
    }
}
