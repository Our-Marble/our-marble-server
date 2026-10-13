package com.sparta.ourmarbleserver.bot.service;

import com.sparta.ourmarbleserver.game.dto.PropertyData;
import com.sparta.ourmarbleserver.game.service.GameDataService;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.PlayerState;
import com.sparta.ourmarbleserver.game.state.PropertyState;
import com.sparta.ourmarbleserver.property.domain.BuildingLevel;
import com.sparta.ourmarbleserver.property.service.PropertyService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기본 봇 판단 테스트. 봇은 1번 플레이어, 상대는 2번 플레이어(0번 칸)이고 시작 현금은 500,000이다.
 * 가격표: 땅 101 방콕(1번 칸, 수익 비율 0.26), 104 독도(4번 칸, 1.5, 건설 불가), 131 서울(31번 칸, 1.15) 등.
 * 보드: 16번 칸은 적립금, 24번 칸은 세계여행이다.
 */
class BasicBotStrategyTest {

    private static final long BOT = 1L;
    private static final long OPPONENT = 2L;

    private final GameDataService data = new GameDataService(JsonMapper.builder().build());
    private final BasicBotStrategy bot = new BasicBotStrategy(data, new PropertyService(data));

    /** 봇이 botPosition 칸에 botMoney를 가지고 있고, 상대는 opponentPosition 칸에 있는 게임 상태 */
    private GameState state(int botPosition, long botMoney, int opponentPosition) {
        GameState state = new GameState("r1");
        PlayerState botState = new PlayerState(BOT);
        botState.setPosition(botPosition);
        botState.setMoney(botMoney);
        state.addPlayer(botState);
        PlayerState opponent = new PlayerState(OPPONENT);
        opponent.setPosition(opponentPosition);
        opponent.setMoney(500_000);
        state.addPlayer(opponent);
        for (PropertyData property : data.getProperties()) {
            state.addProperty(new PropertyState(property.id()));
        }
        return state;
    }

    private void own(GameState state, int propertyId, long ownerId, BuildingLevel level) {
        PropertyState property = state.getPropertyState(propertyId).orElseThrow();
        property.setOwnerId(ownerId);
        property.setBuildingLevel(level);
    }

    /** 상대가 1~12칸 앞의 땅 106, 107, 109를 호텔로 가진 상태. 봇이 0번 칸이면 다음 턴에 통행료 27만~36만을 낼 확률이 크다. */
    private GameState stateWithRiskyBoard(long botMoney) {
        GameState state = state(0, botMoney, 20);
        own(state, 106, OPPONENT, BuildingLevel.HOTEL);
        own(state, 107, OPPONENT, BuildingLevel.HOTEL);
        own(state, 109, OPPONENT, BuildingLevel.HOTEL);
        return state;
    }

    // ===== 구매 =====

    @Test
    void 좋은_땅이고_현금이_충분하면_산다() {
        GameState state = state(4, 500_000, 0);

        assertThat(bot.shouldPurchase(state, BOT, 104, 200_000)).isTrue();   // 독도: 수익 비율 1.5
    }

    @Test
    void 현금이_모자라면_사지_않는다() {
        GameState state = state(4, 150_000, 0);

        assertThat(bot.shouldPurchase(state, BOT, 104, 200_000)).isFalse();
    }

    @Test
    void 다음_턴_통행료를_못_낼_위험이_크면_사지_않고_안전하면_산다() {
        // 방콕(5만)을 사면 남는 현금 25만으로는 다음 턴에 상대의 호텔 땅(27만~36만)을 밟을 때 현금이 모자란다
        assertThat(bot.shouldPurchase(stateWithRiskyBoard(300_000), BOT, 101, 50_000)).isFalse();
        // 현금이 55만 남으면 어느 호텔 통행료도 낼 수 있다
        assertThat(bot.shouldPurchase(stateWithRiskyBoard(600_000), BOT, 101, 50_000)).isTrue();
    }

    @Test
    void 보통_땅은_남는_현금이_적으면_사지_않지만_막판에는_산다() {
        GameState early = state(16, 150_000, 0);   // 방콕(수익 비율 0.26): 사고 나면 현금 10만이 남는다
        GameState endGame = state(16, 150_000, 0);
        endGame.setRoundNumber(28);                // 최대 라운드 30에서 남은 라운드 2 (막판)

        assertThat(bot.shouldPurchase(early, BOT, 101, 50_000)).isFalse();
        assertThat(bot.shouldPurchase(endGame, BOT, 101, 50_000)).isTrue();
    }

    // ===== 건설 =====

    @Test
    void 건설_불가_땅과_호텔은_짓지_않는다() {
        GameState tourist = state(4, 500_000, 0);
        own(tourist, 104, BOT, BuildingLevel.LAND);   // 독도: 건설 불가
        GameState hotel = state(31, 5_000_000, 0);
        own(hotel, 131, BOT, BuildingLevel.HOTEL);

        assertThat(bot.shouldBuild(tourist, BOT, 104, 0)).isFalse();
        assertThat(bot.shouldBuild(hotel, BOT, 131, 0)).isFalse();
    }

    @Test
    void 통행료_상승분으로_빨리_회수할_땅은_짓는다() {
        GameState state = state(31, 500_000, 0);
        own(state, 131, BOT, BuildingLevel.LAND);   // 서울 별장 15만: 통행료 +12.8만, 8라운드면 회수

        assertThat(bot.shouldBuild(state, BOT, 131, 150_000)).isTrue();
    }

    @Test
    void 회수가_느린_건설은_남는_현금이_넉넉할_때만_한다() {
        GameState tight = state(1, 200_000, 0);     // 방콕 별장 5만: 통행료 +8천이라 회수에 40라운드 넘게 걸린다
        own(tight, 101, BOT, BuildingLevel.LAND);
        GameState rich = state(1, 400_000, 0);
        own(rich, 101, BOT, BuildingLevel.LAND);

        assertThat(bot.shouldBuild(tight, BOT, 101, 50_000)).isFalse();   // 남는 현금 15만 < 20만
        assertThat(bot.shouldBuild(rich, BOT, 101, 50_000)).isTrue();     // 남는 현금 35만 >= 20만
    }

    // ===== 인수 =====

    @Test
    void 통행료로_빨리_회수할_땅만_인수한다() {
        GameState fast = state(4, 1_000_000, 0);
        own(fast, 104, OPPONENT, BuildingLevel.LAND);    // 독도: 통행료 30만, 인수가 40만
        GameState slowHotel = state(27, 5_000_000, 0);
        own(slowHotel, 127, OPPONENT, BuildingLevel.HOTEL);   // 파리 호텔: 인수가 240만, 회수 14라운드
        GameState slowLand = state(3, 1_000_000, 0);
        own(slowLand, 103, OPPONENT, BuildingLevel.LAND);     // 베이징 땅: 통행료 4천

        assertThat(bot.shouldAcquire(fast, BOT, 104, 400_000)).isTrue();
        assertThat(bot.shouldAcquire(slowHotel, BOT, 127, 2_400_000)).isFalse();
        assertThat(bot.shouldAcquire(slowLand, BOT, 103, 160_000)).isFalse();
    }

    // ===== 매각 =====

    @Test
    void 매각은_손해가_적은_땅부터_부족분만큼만_판다() {
        GameState state = state(3, 10_000, 0);
        own(state, 101, BOT, BuildingLevel.LAND);   // 매각가 25,000
        own(state, 106, BOT, BuildingLevel.LAND);   // 매각가 50,000
        own(state, 131, BOT, BuildingLevel.LAND);   // 매각가 175,000

        // 3만이 필요하고 현금이 1만이라 부족분 2만: 통행료 손실이 가장 적은 방콕 하나면 된다
        assertThat(bot.chooseSellProperties(state, BOT, 30_000)).containsExactly(101);
    }

    @Test
    void 현금이_충분하면_팔_땅이_없다() {
        GameState state = state(3, 10_000, 0);
        own(state, 101, BOT, BuildingLevel.LAND);

        assertThat(bot.chooseSellProperties(state, BOT, 5_000)).isEmpty();
    }

    @Test
    void 한_곳으로_모자라면_여러_곳을_판다() {
        GameState state = state(3, 10_000, 0);
        own(state, 101, BOT, BuildingLevel.LAND);   // 25,000
        own(state, 106, BOT, BuildingLevel.LAND);   // 50,000

        // 부족분 6만: 둘을 합쳐야 7.5만으로 채워진다
        assertThat(bot.chooseSellProperties(state, BOT, 70_000)).containsExactlyInAnyOrder(101, 106);
    }

    // ===== 세계여행 =====

    @Test
    void 적립금이_기준_이상이면_적립금_칸으로_간다() {
        GameState state = state(24, 500_000, 0);
        state.setWelfareFund(300_000);

        assertThat(bot.chooseTravelDestination(state, BOT)).isEqualTo(16);
    }

    @Test
    void 적립금이_없으면_수익_비율이_가장_높은_빈_땅으로_간다() {
        GameState state = state(24, 500_000, 0);

        assertThat(bot.chooseTravelDestination(state, BOT)).isEqualTo(4);   // 독도(1.5): 같은 비율에서는 앞선 칸
    }

    @Test
    void 남의_땅은_제외하고_다음으로_좋은_땅으로_간다() {
        GameState state = state(24, 500_000, 0);
        for (int propertyId : new int[]{104, 113, 118, 125}) {   // 수익 비율 1.5인 땅 넷을 상대가 가졌다
            own(state, propertyId, OPPONENT, BuildingLevel.LAND);
        }

        assertThat(bot.chooseTravelDestination(state, BOT)).isEqualTo(31);   // 서울(1.15)
    }
}