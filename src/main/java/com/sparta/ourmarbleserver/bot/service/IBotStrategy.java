package com.sparta.ourmarbleserver.bot.service;


import com.sparta.ourmarbleserver.game.state.GameState;

import java.util.List;

/**
 * 봇의 결정만 담당한다. GameState를 읽기만 하고 절대 직접 바꾸지 않는다.
 * 상태 변경은 항상 GameService가 처리한다.
 */
public interface IBotStrategy {

    /** 빈 땅을 살지 결정한다. */
    boolean shouldPurchase(GameState state, long botId, int propertyId, long price);

    /** 내 땅에 다음 단계 건물을 지을지 결정한다. */
    boolean shouldBuild(GameState state, long botId, int propertyId, long price);

    /** 통행료를 낸 뒤 남의 땅을 인수할지 결정한다. */
    boolean shouldAcquire(GameState state, long botId, int propertyId, long price);

    /** 현금이 requiredAmount보다 부족할 때 팔 땅을 고른다. 선택한 땅의 propertyId 목록을 돌려준다. */
    List<Integer> chooseSellProperties(GameState state, long botId, long requiredAmount);

    /** 세계여행 칸에서 이동할 목적지 칸 번호를 고른다. */
    int chooseTravelDestination(GameState state, long botId);
}
