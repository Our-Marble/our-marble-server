package com.sparta.ourmarbleserver.game.service;


import com.sparta.ourmarbleserver.game.state.GameState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class DiceService {

    /** 주사위 결과*/
    public record DiceResult(int dice1, int dice2) {
        public boolean isDouble() {
            return dice1 == dice2;
        }

        public int sum() {
            return dice1 + dice2;
        }
    }

    private final Random random;

    public DiceService() {
        this(new Random());
    }

    /** 테스트에서 주사위 값을 고정하려고 Random을 넣는 생성자 */
    public DiceService(Random random) {
        this.random = random;
    }

    /**
     * 주사위 2개를 굴리고 방 상태의 더블 정보를 갱신한다.
     * 더블이면 isDouble=true, 연속 더블 횟수 +1 / 아니면 isDouble=false, 횟수 0.
     */
    public DiceResult roll(GameState state) {
        DiceResult result = new DiceResult(random.nextInt(6) + 1, random.nextInt(6) + 1);

        if (result.isDouble()) {
            state.setDouble(true);
            state.setConsecutiveDoubleCount(state.getConsecutiveDoubleCount() + 1);
        } else {
            state.setDouble(false);
            state.setConsecutiveDoubleCount(0);
        }
        return result;
    }
    /** 0 이상 bound 미만의 무작위 번호. (황금열쇠 카드 뽑기용) 주사위와 같은 Random을 쓴다. */
    public int randomIndex(int bound) {
        return random.nextInt(bound);
    }
}
