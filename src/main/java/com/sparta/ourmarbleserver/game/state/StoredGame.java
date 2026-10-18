package com.sparta.ourmarbleserver.game.state;

import com.sparta.ourmarbleserver.property.domain.BuildingLevel;

import java.util.List;

/**
 * Redis에 저장하는 게임 상태 모양. GameState는 목록을 밖에 열지 않으므로 저장용 record를 따로 둔다.
 * GameState / PlayerState / PropertyState에 필드를 추가하면 여기에도 추가해야 한다. (StoredGameTest가 잡아 준다)
 * boolean record 이름에 is를 붙이지 않는다. (Jackson이 record의 isXxx를 “xxx라는 속성의 getter”로 해석하는 규칙이 존재)
 */
public record StoredGame(String roomId, int turnNumber, int roundNumber, long currentPlayerId, long welfareFund,
                         TurnPhase phase, boolean doubleRolled, int consecutiveDoubleCount, boolean gameOver,
                         GameConfig config, List<Long> playerOrder, List<Player> players, List<Property> properties) {

    public record Player(long playerId, int position, long money, int islandTurnsRemaining, boolean bankrupt,
                         String nickname, boolean bot, int finalRank) {
    }

    public record Property(int propertyId, Long ownerId, BuildingLevel buildingLevel) {
    }

    public static StoredGame from(GameState state) {
        List<Player> players = state.players().stream()
                .map(p -> new Player(p.getPlayerId(), p.getPosition(), p.getMoney(), p.getIslandTurnsRemaining(),
                        p.isBankrupt(), p.getNickname(), p.isBot(), p.getFinalRank()))
                .toList();
        List<Property> properties = state.properties().stream()
                .map(p -> new Property(p.getPropertyId(), p.getOwnerId(), p.getBuildingLevel()))
                .toList();
        return new StoredGame(state.getRoomId(), state.getTurnNumber(), state.getRoundNumber(),
                state.getCurrentPlayerId(), state.getWelfareFund(), state.getPhase(), state.isDouble(),
                state.getConsecutiveDoubleCount(), state.isGameOver(), state.getConfig(),
                List.copyOf(state.playerOrder()), players, properties);
    }

    public GameState toState() {
        GameState state = new GameState(roomId);
        state.setTurnNumber(turnNumber);
        state.setRoundNumber(roundNumber);
        state.setCurrentPlayerId(currentPlayerId);
        state.setWelfareFund(welfareFund);
        state.setPhase(phase);
        state.setDouble(doubleRolled);
        state.setConsecutiveDoubleCount(consecutiveDoubleCount);
        state.setGameOver(gameOver);
        state.setConfig(config);
        state.setPlayerOrder(playerOrder);
        for (Player p : players) {
            PlayerState player = new PlayerState(p.playerId());
            player.setPosition(p.position());
            player.setMoney(p.money());
            player.setIslandTurnsRemaining(p.islandTurnsRemaining());
            player.setBankrupt(p.bankrupt());
            player.setNickname(p.nickname());
            player.setBot(p.bot());
            player.setFinalRank(p.finalRank());
            state.addPlayer(player);
        }
        for (Property p : properties) {
            PropertyState property = new PropertyState(p.propertyId());
            property.setOwnerId(p.ownerId());
            property.setBuildingLevel(p.buildingLevel());
            state.addProperty(property);
        }
        return state;
    }
}