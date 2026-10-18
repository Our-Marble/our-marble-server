package com.sparta.ourmarbleserver.bot.service;

import com.sparta.ourmarbleserver.bot.domain.BotSettings;
import com.sparta.ourmarbleserver.game.dto.PropertyData;
import com.sparta.ourmarbleserver.game.dto.TileData;
import com.sparta.ourmarbleserver.game.service.GameDataService;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.game.state.PlayerState;
import com.sparta.ourmarbleserver.game.state.PropertyState;
import com.sparta.ourmarbleserver.property.domain.BuildingLevel;
import com.sparta.ourmarbleserver.property.service.PropertyService;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 기본 봇 전략. 판단 기준값은 BotSettings에서 읽으므로, 성향마다 같은 로직을 다른 값으로 쓴다.
 * 돈을 쓰고 난 뒤 다음 턴에 통행료를 현금으로 못 낼 위험이 크면 구매·건설·인수를 하지 않고, 땅은 수익 비율이 좋은 땅 위주로 산다.
 * 매각할 때는 상대가 밟을 확률까지 고려해 기대 통행료 손실이 적은 땅부터 판다.
 * 건설·인수는 늘어나는 통행료로 비용을 회수하는 기간(라운드)을 따져 결정한다.
 * 돈을 쓰면 다음 턴에 만날 더 좋은 땅을 못 사게 될 확률이 크면 이번에는 아낀다.
 * 막판에는 구매·건설을 적극적으로 하고, 남은 라운드 안에 회수 못 할 인수는 하지 않는다.
 * 상태를 갖지 않아서 모든 방이 하나의 객체를 같이 쓴다. (최대 라운드는 방의 GameConfig에서 읽는다)
 */
@Service
public class BasicBotStrategy implements IBotStrategy {

    // 성향과 무관한 게임 규칙 값
    private static final int DEFAULT_TRAVEL_DESTINATION = 16;  // 적립금 칸 (갈 곳이 없을 때 기본 목적지)
    private static final double AVERAGE_HIT_RATE = 1.0 / 7;    // 주사위 평균 7칸 -> 한 번 굴릴 때 특정칸에 도착할 확률

    /**
     * 세계여행 목적지 후보의 우선순위(tier, 작을수록 좋음)와 같은 순위 안에서 비교할 점수
     */
    private record DestinationScore(int tier, double score) {
    }

    private final TileData[] tileByIndex;                       // 칸 번호 → 칸 정보
    private final Map<Integer, PropertyData> propertyData = new HashMap<>();
    private final PropertyService propertyService;
    private final BotSettings settings = BotSettings.balanced();

    public BasicBotStrategy(GameDataService gameDataService, PropertyService propertyService) {
        this.propertyService = propertyService;

        // 리스트 순서와 칸 번호가 달라도 안전하도록 index 기준으로 다시 배치한다.
        List<TileData> tiles = gameDataService.getTiles();
        tileByIndex = new TileData[tiles.size()];
        for (TileData tile : tiles) {
            if (tile.index() >= 0 && tile.index() < tileByIndex.length) { tileByIndex[tile.index()] = tile; }
        }
        for (PropertyData data : gameDataService.getProperties()) {
            propertyData.put(data.id(), data);
        }
    }

    // ===== 판단 (IBotStrategy) =====

    @Override
    public boolean shouldPurchase(GameState state, long botId, int propertyId, long price) {
        // 1) 통행료 위험이 크면 어떤 땅이든 사지 않는다.
        if (!canAffordSafely(state, botId, price)) { return false; }

        PropertyData data = propertyData.get(propertyId);
        PlayerState bot = findPlayer(state, botId);
        if (data == null || bot == null) { return true; }

        // 2) 좋은 땅은 사고, 보통 땅은 현금이 넉넉히 남을 때만 산다.
        double landReturn = maxTollReturn(data);
        long cashAfter = bot.getMoney() - price;
        boolean buy = isWorthBuying(state, data, cashAfter);

        // 3) 사면 다음 턴에 만날 더 좋은 땅을 못 사게 될 확률이 크면 아낀다.
        double missed = buy ? missedBetterLandChance(state, bot, price, landReturn + settings.betterLandMargin) : 0;
        if (missed > settings.maxMissedChance) { buy = false; }
        return buy;
    }

    @Override
    public boolean shouldBuild(GameState state, long botId, int propertyId, long cost) {
        // 1) 통행료 위험이 크면 짓지 않는다.
        if (!canAffordSafely(state, botId, cost)) { return false; }

        PropertyState property = state.getPropertyState(propertyId).orElse(null);
        PropertyData data = propertyData.get(propertyId);
        PlayerState bot = findPlayer(state, botId);
        if (property == null || data == null || bot == null) { return true; }
        if (!data.canBuild() || property.getBuildingLevel() == BuildingLevel.HOTEL) { return false; }

        // 2) 통행료 상승분으로 건설비를 빨리 회수할 수 있거나, 현금이 넉넉하면 짓는다.
        BuildingLevel next = BuildingLevel.fromIndex(property.getBuildingLevel().index() + 1);
        long tollGain = data.tolls().get(next.index()) - data.tolls().get(property.getBuildingLevel().index());
        double payback = paybackRounds(state, botId, propertyId, cost, tollGain);
        long cashAfter = bot.getMoney() - cost;
        boolean build = payback <= settings.maxPaybackRounds || cashAfter >= settings.safeCashReserve || isEndGame(state);

        // 3) 지으면 다음 턴에 만날 좋은 땅을 못 사게 될 확률이 크면 아낀다.
        double missed = build ? missedBetterLandChance(state, bot, cost, settings.goodLandReturn) : 0;
        if (missed > settings.maxMissedChance) { build = false; }
        return build;
    }

    @Override
    public boolean shouldAcquire(GameState state, long botId, int propertyId, long price) {
        // 1) 통행료 위험이 크면 인수하지 않는다.
        if (!canAffordSafely(state, botId, price)) { return false; }

        PropertyState property = state.getPropertyState(propertyId).orElse(null);
        PropertyData data = propertyData.get(propertyId);
        PlayerState bot = findPlayer(state, botId);
        if (property == null || data == null || bot == null) { return true; }

        // 2) 인수가가 비싸므로, 통행료 수입으로 빨리 회수할 수 있는 땅만 인수한다.
        long toll = propertyService.getToll(property);
        double payback = paybackRounds(state, botId, propertyId, price, toll);
        boolean acquire = payback <= Math.min(settings.maxPaybackRounds, remainingRounds(state));

        // 3) 인수하면 다음 턴에 만날 좋은 땅을 못 사게 될 확률이 크면 아낀다.
        double missed = acquire ? missedBetterLandChance(state, bot, price, settings.goodLandReturn) : 0;
        if (missed > settings.maxMissedChance) { acquire = false; }
        return acquire;
    }

    /**
     * 매각가 1원당 잃는 기대 통행료가 적은 땅부터 담아 부족분을 채운다.
     * 그 뒤 지킬 가치가 큰 땅부터, 빼도 부족분이 채워지면 다시 빼서 수익이 좋은 땅을 최대한 지킨다.
     */
    @Override
    public List<Integer> chooseSellProperties(GameState state, long botId, long requireAmount) {
        long shortage = requireAmount - getMoney(state, botId);

        List<PropertyState> owned = new ArrayList<>(state.properties().stream().filter(property -> property.isOwnedBy(botId)).toList());
        owned.sort(Comparator.comparingDouble(property -> tollLossPerWon(state, botId, property)));

        List<PropertyState> selected = new ArrayList<>();
        long sum = 0;
        for (PropertyState property : owned) {
            if (sum >= shortage) { break; }
            selected.add(property);
            sum += propertyService.getSellValue(property);
        }

        // 지킬 가치가 큰 땅(기대 통행료 손실이 큰 땅)부터, 빼도 부족분을 채운다면 매각 목록에서 제외한다.
        selected.sort(Comparator.comparingDouble((PropertyState property) -> tollLossPerWon(state, botId, property)).reversed());
        for (int i = 0; i < selected.size(); ) {
            long value = propertyService.getSellValue(selected.get(i));
            if (sum - value >= shortage) {
                sum -= value;
                selected.remove(i);
            } else { i++; }
        }
        return selected.stream().map(PropertyState::getPropertyId).toList();
    }

    /**
     * 보드의 모든 칸을 평가해서 우선순위(tier)가 가장 높고, 같은 순위에서는 점수가 가장 높은 칸으로 간다.
     * 갈 만한 칸이 없으면 기본 목적지로 간다.
     */
    @Override
    public int chooseTravelDestination(GameState state, long botId) {
        PlayerState bot = findPlayer(state, botId);
        if (bot == null || tileByIndex.length == 0) { return DEFAULT_TRAVEL_DESTINATION; }

        int bestPosition = -1;
        int bestTier = Integer.MAX_VALUE;
        double bestScore = -Double.MAX_VALUE;

        for (int position = 0; position < tileByIndex.length; position++) {
            if (position == bot.getPosition()) { continue; }
            DestinationScore destination = scoreDestination(state, bot, position);
            if (destination == null) { continue; }
            if (destination.tier < bestTier || destination.tier() == bestTier && destination.score() > bestScore) {
                bestPosition = position;
                bestTier = destination.tier();
                bestScore = destination.score();
            }
        }
        return bestPosition < 0 ? DEFAULT_TRAVEL_DESTINATION : bestPosition;
    }

    // ===== 통행료 위험 (내가 낼 돈) =====

    /**
     * cost를 낼 현금이 있고, 낸 뒤 다음 턴에 통행료를 현금으로 못 낼 확률이 기준 이하인지 확인한다.
     */
    private boolean canAffordSafely(GameState state, long botId, long cost) {
        PlayerState bot = findPlayer(state, botId);
        if (bot == null || bot.getMoney() < cost) { return false; }
        double risk = cashShortProbability(state, botId, bot.getPosition(), bot.getMoney() - cost);
        return risk <= settings.maxCashShortProbability;
    }

    /**
     * position 칸에서 cost를 쓰고 난 뒤, 다음 턴 현금 부족 확률이 기준 이하인지 확인한다.
     */
    private boolean isSafeToSpendAt(GameState state, PlayerState bot, int position, long cost) {
        if (bot.getMoney() < cost) { return false; }
        return cashShortProbability(state, bot.getPlayerId(), position, bot.getMoney() - cost) <= settings.maxCashShortProbability;
    }

    /**
     * 다음 주사위(합 2~12)로 도착할 칸 중, 통행료가 cash보다 비싼 남의 땅에 도착할 확률
     */
    private double cashShortProbability(GameState state, long botId, int position, long cash) {
        if (tileByIndex.length == 0) { return 0; }
        double probability = 0;
        for (int sum = 2; sum <= 12; sum++) {
            int target = (position + sum) % tileByIndex.length;
            if (opponentToll(state, botId, target) > cash) {
                probability += diceSumProbability(sum);
            }
        }
        return probability;
    }

    /**
     * position 칸이 다른 플레이어 소유 땅이면 통행료를, 아니면 0을 돌려준다.
     */
    private long opponentToll(GameState state, long botId, int position) {
        TileData tile = tileByIndex[position];
        if (tile == null || !"PROPERTY".equals(tile.type())) { return 0; }
        PropertyState property = state.getPropertyState(tile.propertyId()).orElse(null);
        if (property == null || !property.hasOwner() || property.isOwnedBy(botId)) { return 0; }
        return propertyService.getToll(property);
    }

    // ===== 땅 가치 (구매·기회비용) =====

    /**
     * 땅의 수익 비율 = 최대 통행료 ÷ 그 통행료까지 드는 총 투자금.
     * 건설 가능한 땅은 호텔 기준, 건설 불가 땅은 땅 통행료 기준이다.
     */
    private static double maxTollReturn(PropertyData data) {
        if (!data.canBuild()) {
            return data.landPrice() > 0 ? (double) data.tolls().get(BuildingLevel.LAND.index()) / data.landPrice() : 0;
        }
        long invested = data.landPrice() + data.buildCosts().stream().mapToLong(Long::longValue).sum();
        return invested > 0 ? (double) data.tolls().get(BuildingLevel.HOTEL.index()) / invested : 0;
    }

    /**
     * 구매 규칙: 좋은 땅이면 사고, 보통 땅은 사고 나서 현금이 넉넉히 남을 때만 산다.
     * 막판에는 땅값이 그대로 총자산에 잡혀 손해가 없으므로 보통 땅도 산다.
     */
    private boolean isWorthBuying(GameState state, PropertyData data, long cashAfter) {
        return maxTollReturn(data) >= settings.goodLandReturn || cashAfter >= settings.safeCashReserve || isEndGame(state);
    }

    /**
     * cost를 쓰면 다음 턴에 도착할 수 있는 더 좋은 빈 땅을 못 사게 되는 확률.
     * "더 좋은 땅"은 맵 위치와 무관하게, 수익 비율(maxTollReturn)이 minReturn 이상인 땅이다.
     * - 구매: minReturn = 지금 땅의 수익 비율 + betterLandMargin
     * - 건설·인수: minReturn = goodLandReturn
     * 지금 현금으로는 살 수 있는데, cost를 쓰고 나면 못 사게 되는 땅만 센다.
     */
    private double missedBetterLandChance(GameState state, PlayerState bot, long cost, double minReturn) {
        if (tileByIndex.length == 0) { return 0; }
        long cashAfter = bot.getMoney() - cost;
        double chance = 0;
        for (int sum = 2; sum <= 12; sum++) {
            int targetPosition = (bot.getPosition() + sum) % tileByIndex.length;
            TileData tile = tileByIndex[targetPosition];
            if (tile == null || !"PROPERTY".equals(tile.type())) { continue; }
            PropertyState target = state.getPropertyState(tile.propertyId()).orElse(null);
            PropertyData data = propertyData.get(tile.propertyId());
            if (target == null || data == null || target.hasOwner()) { continue; }
            if (maxTollReturn(data) < minReturn) { continue; }
            if (bot.getMoney() >= data.landPrice() && cashAfter < data.landPrice()) {
                chance += diceSumProbability(sum) * stillEmptyChance(state, bot.getPlayerId(), targetPosition);
            }
        }
        return chance;

    }

    /**
     * 내 다음 차례까지 position 칸의 빈 땅이 남아 있을 확률.
     * 상대가 먼저 밟으면 사 간다고 보고, 모든 상대가 다음 주사위로 그 칸을 안 밟을 확률을 곱한다.
     */
    private double stillEmptyChance(GameState state, long botId, int position) {
        double stillEmpty = 1;
        for (PlayerState opponent : state.players()) {
            if (opponent.getPlayerId() == botId || opponent.isBankrupt()) { continue; }
            int distance = (position -  opponent.getPosition() + tileByIndex.length) % tileByIndex.length;
            stillEmpty *= 1 - nextRollHitProbability(opponent,distance);
        }
        return stillEmpty;
    }

    // ===== 통행료 수입 (상대가 밟을 확률) =====

    /** 살아 있는 상대들이 이 땅에 도착할 확률의 합. (장기 평균 + 다음 주사위 기준 실제 확률) */
    private double opponentHitRate(GameState state, long botId, int propertyId) {
        int tilePosition = findTilePosition(propertyId);
        if (tilePosition < 0) { return 0; }
        double rate = 0;
        for (PlayerState opponent : state.players()) {
            if (opponent.getPlayerId() == botId || opponent.isBankrupt()) { continue; }
            int distance = (tilePosition - opponent.getPosition() + tileByIndex.length) % tileByIndex.length;
            rate += AVERAGE_HIT_RATE + nextRollHitProbability(opponent, distance);
        }
        return rate;
    }

    /** 땅 번호에 해당하는 칸 번호. 없으면 -1 */
    private int findTilePosition(int propertyId) {
        for (int position = 0; position < tileByIndex.length; position++) {
            TileData tile = tileByIndex[position];
            if (tile != null && "PROPERTY".equals(tile.type()) && tile.propertyId() != null && tile.propertyId() == propertyId) {
                return position;
            }
        }
        return -1;
    }

    /**
     * 상대가 다음 주사위 한 번으로 distance칸 앞에 도착할 확률.
     * 무인도에 갇힌 상대는 더블이 나와야만 움직이므로, 짝수 거리(2~12)만 각각 1/36이다.
     */
    private static double nextRollHitProbability(PlayerState opponent, int distance) {
        if (distance < 2 || distance > 12) {
            return 0;
        }
        if (opponent.getIslandTurnsRemaining() > 0) {
            return distance % 2 == 0 ? 1.0 / 36 : 0;
        }
        return diceSumProbability(distance);
    }

    /**
     * 투자 비용을 통행료로 회수하는 데 걸리는 라운드 수. (건설·인수)
     * 한 라운드 기대 수입 = 통행료 × 상대들이 한 라운드에 이 땅을 밟을 확률의 합
     */
    private double paybackRounds(GameState state, long botId, int propertyId, long cost, long toll) {
        double incomePerRound = toll * opponentHitRate(state, botId, propertyId);
        return incomePerRound > 0 ? cost / incomePerRound : Double.MAX_VALUE;
    }

    /**
     * 이 땅을 팔 때 매각가 1원당 잃는 기대 통행료. 값이 작을수록 팔아도 손해가 적은 땅이다. (매각)
     * 기대 통행료 = 통행료 × (상대별 장기 평균 도착 확률 + 다음 턴 실제 도착 확률)
     */
    private double tollLossPerWon(GameState state, long botId, PropertyState property) {
        long sellValue = propertyService.getSellValue(property);
        if (sellValue <= 0) {
            return Double.MAX_VALUE;   // 팔아도 돈이 안 되는 땅은 맨 뒤로
        }
        double hitRate = opponentHitRate(state, botId, property.getPropertyId());
        return propertyService.getToll(property) * hitRate / sellValue;
    }

    // ===== 세계여행 목적지 평가 =====

    /**
     * 목적지 후보 칸의 우선순위(tier, 작을수록 좋음)와 같은 순위 안에서 비교할 점수를 구한다.
     * 가면 손해인 칸은 null을 돌려 후보에서 뺀다.
     */
    private DestinationScore scoreDestination(GameState state, PlayerState bot, int position) {
        TileData tile = tileByIndex[position];
        if (tile == null) { return null; }
        return switch (tile.type()) {
            case "CHARITY" -> {
                long fund = state.getWelfareFund();
                if (fund <= 0) {
                    yield new DestinationScore(5, 0);   // 받을 게 없으면 중립
                }
                yield new DestinationScore(fund >= settings.welfareFundWorthTrip ? 1 : 4, fund);
            }
            case "GOLDEN_KEY", "START" -> new DestinationScore(5, 0);   // 중립 (세계여행은 월급 없음)
            case "PROPERTY" -> scorePropertyDestination(state, bot, position, tile.propertyId());
            default -> null;   // 무인도, 세무조사, 세계여행
        };
    }

    /** 땅 칸 평가: 살 만한 빈 땅(2순위), 건물을 올릴 내 땅(3순위), 그 외 빈 땅·내 땅(중립), 남의 땅(제외). */
    private DestinationScore scorePropertyDestination(GameState state, PlayerState bot, int position, int propertyId) {
        PropertyState property = state.getPropertyState(propertyId).orElse(null);
        PropertyData data = propertyData.get(propertyId);
        if (property == null || data == null) { return null; }

        // 빈 땅: 구매 규칙을 통과하면 수익 비율로 비교
        if (!property.hasOwner()) {
            long price = data.landPrice();
            if (isSafeToSpendAt(state, bot, position, price) && isWorthBuying(state, data, bot.getMoney() - price)) {
                return new DestinationScore(2, maxTollReturn(data));
            }
            return new DestinationScore(5, 0);
        }

        // 내 땅: 다음 단계를 지을 수 있으면 통행료 상승폭으로 비교
        if (property.isOwnedBy(bot.getPlayerId())) {
            if (data.canBuild() && property.getBuildingLevel() != BuildingLevel.HOTEL) {
                BuildingLevel next = BuildingLevel.fromIndex(property.getBuildingLevel().index() + 1);
                long cost = propertyService.getBuildCost(propertyId, next);
                if (isSafeToSpendAt(state, bot, position, cost)) {
                    long tollGain = data.tolls().get(next.index()) - data.tolls().get(property.getBuildingLevel().index());
                    return new DestinationScore(3, tollGain);
                }
            }
            return new DestinationScore(5, 0);
        }

        return null;   // 남의 땅은 통행료만 내므로 제외
    }

    // ===== 공용 =====

    /**
     * 주사위 두 개의 합이 sum일 확률. (7이 6/36으로 가장 높고, 2와 12가 1/36으로 가장 낮음)
     */
    private static double diceSumProbability(int sum) {
        return (6 - Math.abs(sum - 7)) / 36.0;
    }

    private static PlayerState findPlayer(GameState state, long botId) {
        return state.players().stream().filter(player -> player.getPlayerId() == botId).findFirst().orElse(null);
    }

    private static long getMoney(GameState state, long botId) {
        PlayerState player = findPlayer(state, botId);
        return player != null ? player.getMoney() : 0;
    }

    /**
     * 이번 라운드 이후 남은 라운드 수. 최대 라운드가 없으면 제한 없음으로 본다.
     */
    private int remainingRounds(GameState state) {
        int maxRound = state.getConfig().maxRound();
        return maxRound > 0 ? Math.max(0, maxRound - state.getRoundNumber()) : Integer.MAX_VALUE;
    }

    private boolean isEndGame(GameState state) {
        return remainingRounds(state) <= settings.endGameRounds;
    }
}