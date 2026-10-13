package com.sparta.ourmarbleserver.bot.service;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 봇을 방에 넣을 때 필요한 번호와 이름을 만든다. (로비가 봇을 추가할 때 부른다)
 * 번호는 실제 유저 번호(양수)와 겹치지 않도록 음수로 주고, 서버 전체에서 하나도 겹치지 않는다.
 * 게임은 번호 하나로 플레이어와 게임을 찾으므로, 서로 다른 방의 봇끼리도 번호가 같으면 안 된다.
 */
public class BotService {
    private static final String NICKNAME_PREFIX = "봇";
    private final AtomicLong sequence = new AtomicLong();

    /** 서버 전체에서 겹치지 않는 봇 번호. (-1, -2, -3, ...) */
    public long nextBotId() {
        return -sequence.incrementAndGet();
    }

    /** 방 안에서 겹치지 않는 봇 이름. 이미 쓰는 이름(usedNicknames)을 뺀 가장 작은 번호를 붙인다. ("봇1", "봇2", ...) */
    public String nextNickname(Collection<String> usedNickname) {
        int number = 1;
        while (usedNickname.contains(NICKNAME_PREFIX + number)) { number++; }
        return NICKNAME_PREFIX + number;
    }

    /** 이 번호가 봇 번호인지 */
    public static boolean isBotId(long playerId) {
        return playerId < 0;
    }
}
