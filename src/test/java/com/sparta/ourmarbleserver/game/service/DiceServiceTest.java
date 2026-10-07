package com.sparta.ourmarbleserver.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.game.service.DiceService.DiceResult;
import com.sparta.ourmarbleserver.game.state.GameState;

class DiceServiceTest {

    /** 정해 둔 주사위 눈(1~6)을 순서대로 돌려주는 Random */
    private static class FixedRandom extends Random {
        private final Queue<Integer> faces = new ArrayDeque<>();

        FixedRandom(int... values) {
            for (int v : values) {
                faces.add(v);
            }
        }

        @Override
        public int nextInt(int bound) {
            return faces.remove() - 1;   // roll()이 +1 해서 쓰므로 눈에서 1을 뺀 값을 준다
        }
    }

    @Test
    void 주사위_눈은_1에서_6_사이다() {
        DiceService service = new DiceService();
        GameState state = new GameState("r1");
        for (int i = 0; i < 1000; i++) {
            DiceResult result = service.roll(state);
            assertThat(result.dice1()).isBetween(1, 6);
            assertThat(result.dice2()).isBetween(1, 6);
        }
    }

    @Test
    void 더블이_아니면_더블_상태가_꺼진다() {
        GameState state = new GameState("r1");
        DiceResult result = new DiceService(new FixedRandom(3, 4)).roll(state);

        assertThat(result.isDouble()).isFalse();
        assertThat(result.sum()).isEqualTo(7);
        assertThat(state.isDouble()).isFalse();
        assertThat(state.getConsecutiveDoubleCount()).isZero();
    }

    @Test
    void 더블이면_더블_상태와_연속_횟수가_오른다() {
        GameState state = new GameState("r1");
        DiceResult result = new DiceService(new FixedRandom(5, 5)).roll(state);

        assertThat(result.isDouble()).isTrue();
        assertThat(state.isDouble()).isTrue();
        assertThat(state.getConsecutiveDoubleCount()).isEqualTo(1);
    }

    @Test
    void 연속_더블은_횟수가_쌓이고_더블이_아니면_초기화된다() {
        GameState state = new GameState("r1");
        DiceService service = new DiceService(new FixedRandom(1, 1, 2, 2, 3, 3, 1, 2));

        service.roll(state);
        service.roll(state);
        service.roll(state);
        assertThat(state.getConsecutiveDoubleCount()).isEqualTo(3);

        service.roll(state);   // 1, 2
        assertThat(state.isDouble()).isFalse();
        assertThat(state.getConsecutiveDoubleCount()).isZero();
    }
}