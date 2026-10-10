package com.sparta.ourmarbleserver.game.state;

/**
 * 게임 규칙의 설정값. 게임(방) 하나마다 하나씩 가지고, 게임이 시작될 때 정해져서 끝날 때까지 바뀌지 않는다.
 * 방장이 로비에서 값을 고르면 그 값으로 만들고, 고르지 않으면 {@link #defaults()}를 쓴다.
 * 클라이언트가 같은 값으로 계산해야 하므로 서버가 기준이고, 클라이언트는 이 값을 따라야 한다.
 *
 * @param startMoney   초기 자금
 * @param salaryAmount 월급 (출발 지점을 지나거나 출발 칸에 도착하면 지급)
 * @param taxAmount    세무조사 세금 (세무조사 칸에 도착하면 부과, 적립금에 쌓임)
 * @param maxRound     최대 라운드. 이 라운드를 넘기면 게임이 끝난다.
 * @param islandTurns  무인도 영업정지 턴 수
 */
public record GameConfig(long startMoney, long salaryAmount, long taxAmount, int maxRound, int islandTurns) {

    public static final long DEFAULT_START_MONEY = 500_000;
    public static final long DEFAULT_SALARY_AMOUNT = 100_000;
    public static final long DEFAULT_TAX_AMOUNT = 100_000;
    public static final int DEFAULT_MAX_ROUND = 30;
    public static final int DEFAULT_ISLAND_TURNS = 3;

    /** 한 게임에 참가할 수 있는 플레이어 수의 한계. 방장이 바꾸는 설정이 아니라 서버 규칙의 한계다. */
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 4;

    /** 값이 범위를 벗어나면 만들 수 없다. (잘못된 설정으로 게임이 시작되는 일을 막는다) */
    public GameConfig {
        if (startMoney < 0) {
            throw new IllegalArgumentException("초기 자금은 0 이상이어야 합니다: " + startMoney);
        }
        if (salaryAmount < 0) {
            throw new IllegalArgumentException("월급은 0 이상이어야 합니다: " + salaryAmount);
        }
        if (taxAmount < 0) {
            throw new IllegalArgumentException("세금은 0 이상이어야 합니다: " + taxAmount);
        }
        if (maxRound < 1) {
            throw new IllegalArgumentException("최대 라운드는 1 이상이어야 합니다: " + maxRound);
        }
        if (islandTurns < 0) {
            throw new IllegalArgumentException("무인도 영업정지 턴은 0 이상이어야 합니다: " + islandTurns);
        }
    }

    /** 기본 설정. 방장이 따로 고르지 않았을 때 쓴다. */
    public static GameConfig defaults() {
        return new GameConfig(DEFAULT_START_MONEY, DEFAULT_SALARY_AMOUNT, DEFAULT_TAX_AMOUNT,
                DEFAULT_MAX_ROUND, DEFAULT_ISLAND_TURNS);
    }
}