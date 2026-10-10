package com.sparta.ourmarbleserver.game.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 게임 설정값(GameConfig) 테스트: 기본값과 값의 범위 검증. */
class GameConfigTest {

    @Test
    void 기본_설정은_지금_서버_값이다() {
        GameConfig config = GameConfig.defaults();

        assertThat(config.startMoney()).isEqualTo(500_000);
        assertThat(config.salaryAmount()).isEqualTo(100_000);
        assertThat(config.taxAmount()).isEqualTo(100_000);
        assertThat(config.maxRound()).isEqualTo(30);
        assertThat(config.islandTurns()).isEqualTo(3);
    }

    @Test
    void 값을_직접_정해서_만들_수_있다() {
        GameConfig config = new GameConfig(1_000_000, 20_000, 50_000, 10, 2);

        assertThat(config.startMoney()).isEqualTo(1_000_000);
        assertThat(config.salaryAmount()).isEqualTo(20_000);
        assertThat(config.taxAmount()).isEqualTo(50_000);
        assertThat(config.maxRound()).isEqualTo(10);
        assertThat(config.islandTurns()).isEqualTo(2);
    }

    @Test
    void 경계값은_허용된다() {
        GameConfig config = new GameConfig(0, 0, 0, 1, 0);

        assertThat(config.maxRound()).isEqualTo(1);
        assertThat(config.islandTurns()).isZero();
    }

    @Test
    void 범위를_벗어난_값은_거부된다() {
        assertThatThrownBy(() -> new GameConfig(-1, 100_000, 100_000, 30, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfig(500_000, -1, 100_000, 30, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfig(500_000, 100_000, -1, 30, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfig(500_000, 100_000, 100_000, 0, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfig(500_000, 100_000, 100_000, 30, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}