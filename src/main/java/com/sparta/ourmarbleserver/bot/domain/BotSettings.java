package com.sparta.ourmarbleserver.bot.domain;

/**
 * 봇 성향마다 달라지는 판단 기준값. BasicBotStrategy는 이 값만 읽어서 판단하므로, 성향을 추가할 때 판단 로직은 건드리지 않는다.
 * 새 성향은 balanced()로 만든 뒤 바꿀 값만 고쳐서 forPersonality에 연결한다.
 */
public class BotSettings {

    public String name = "기본형";                   // 성향 이름

    // ── 통행료 위험 ──
    public double maxCashShortProbability = 0.2;    // 다음 턴 현금 부족 확률이 이보다 크면 돈을 쓰지 않음

    // ── 구매 ──
    public double goodLandReturn = 0.75;            // 최대 통행료 ÷ 총 투자금이 이 이상이면 좋은 땅
    public long safeCashReserve = 200_000;          // 보통 땅 구매·회수가 느린 건설은 이만큼 현금이 남을 때만 진행

    // ── 건설·인수 ──
    public double maxPaybackRounds = 10;             // 투자 비용을 이 라운드 안에 통행료로 회수할 수 있으면 건설·인수

    // ── 기회비용 ──
    public double betterLandMargin = 0.2;            // 구매 시 지금 땅보다 수익 비율이 이만큼 높아야 "더 좋은 땅"
    public double maxMissedChance = 0.15;            // 더 좋은 땅을 놓칠 확률이 이보다 크면 돈을 아낌

    // ── 막판 ──
    public int endGameRounds = 3;                    // 남은 라운드가 이 이하면 막판 (구매·건설 기준 완화)

    // ── 세계여행 ──
    public long welfareFundWorthTrip = 200_000;      // 적립금이 이 이상이면 세계여행 최우선 목적지

    /** 기본형 */
    public static BotSettings balanced() {
        return new BotSettings();
    }

    /** 성향에 맞는 설정값을 만든다. */
    public static BotSettings forPersonality(BotPersonality personality) {
        return switch (personality) {
            case BALANCED -> balanced();
        };
    }
}
