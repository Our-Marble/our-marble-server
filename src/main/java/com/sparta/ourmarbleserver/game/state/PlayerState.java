package com.sparta.ourmarbleserver.game.state;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/** 플레이어 한 명의 게임 중 상태. */
@Getter
@Setter
@RequiredArgsConstructor
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
     * 닉네임. 게임 규칙에는 쓰이지 않고, 재접속한 유저에게 현재 상태를 보낼 때 DB를 다시 부르지 않으려고 들고 있는다.
     * 값을 넣어 주지 않으면 빈 문자열이다.
     */
    private String nickname = "";

    /** 봇 여부. 봇은 서버에서만 움직이고 클라이언트에는 두지 않는다. */
    private boolean bot;

    /**
     * 최종 등수. 0이면 아직 정해지지 않음.
     * - 파산 시: 그 시점에 남아 있던 인원 수 (먼저 파산할수록 낮은 등수)
     * - 게임 종료 시: 살아남은 플레이어에게 1위부터 부여
     */
    private int finalRank;

    /** 현금을 delta만큼 더한다. 줄이려면 음수를 넘긴다. */
    public void addMoney(long delta) {
        this.money += delta;
    }
}