package com.sparta.ourmarbleserver.game.service;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

/**
 * 정해 둔 값을 순서대로 돌려주는 Random. 테스트에서 주사위 눈이나 카드 뽑기 결과를 고정할 때 쓴다.
 * 주사위 눈(1~6)이나 카드 목록 위치 + 1을 넣으면 된다. nextInt가 불릴 때마다 다음 값을 꺼내고, 값이 모자라면 예외가 난다.
 */
class FixedRandom extends Random {

    private final Queue<Integer> values = new ArrayDeque<>();

    FixedRandom(int... values) {
        for (int value : values) {
            this.values.add(value);
        }
    }

    @Override
    public int nextInt(int bound) {
        return values.remove() - 1;   // DiceService가 +1 해서 쓰므로 값에서 1을 뺀 번호를 준다
    }
}