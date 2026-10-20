package com.sparta.ourmarbleserver.game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sparta.ourmarbleserver.game.state.GameConfig;
import com.sparta.ourmarbleserver.game.state.GameState;

import java.util.List;

/**
 * GAME_STATE_SYNC 알림의 내용. 재접속한 플레이어에게 현재 게임 상태 전체를 보내 화면을 복구하게 한다.
 * <pre>
 * { "topic": "GAME", "type": "GAME_STATE_SYNC", "gameState": { "roomId": "W9FS2V", ... } }
 * </pre>
 * 서버 상태(GameState)를 그대로 내보내지 않고 알림 전용 record로 만든다. 필드 이름은 클라이언트의
 * GameState, PlayerState, PropertyState 속성 이름(대소문자 무시)에 맞췄다. (예: playerStates, isBankrupt)
 * 봇 여부(bot)와 초상화 번호(portraitId)는 재접속한 유저가 상대를 그리는 데 필요해서 플레이어마다 보낸다.
 */
public record GameStateSyncPayload(GameSnapshot gameState) {

    /** 시작한 게임 상태에서 알림 내용을 만든다. */
    public static GameStateSyncPayload from(GameState state) {
        List<PlayerSnapshot> players = state.players().stream()
                .map(player -> new PlayerSnapshot(player.getPlayerId(), player.getNickname(), player.getPosition(),
                        player.getMoney(), player.getIslandTurnsRemaining(), player.isBankrupt(), player.getFinalRank(),
                        player.getPortraitId(), player.isBot()))
                .toList();
        List<PropertySnapshot> properties = state.properties().stream()
                .map(property -> new PropertySnapshot(property.getPropertyId(), property.getOwnerId(),
                        property.getBuildingLevel().index()))
                .toList();
        return new GameStateSyncPayload(new GameSnapshot(
                state.getRoomId(), null, null, state.getTurnNumber(), state.getRoundNumber(), state.getCurrentPlayerId(),
                state.getWelfareFund(), state.getPhase().name(), state.isDouble(), state.getConsecutiveDoubleCount(),
                state.playerOrder(), players, properties, state.getConfig()));
    }

    /**
     * 방 정보(방장, 최대 인원)를 채운 알림 내용을 돌려준다. 게임 상태에는 방 정보가 없어서 로비의 방에서 읽어 붙인다.
     * 방 정보를 붙이지 않으면 두 필드는 JSON에서 빠지고, 클라는 플레이어 수로 대신 표시한다.
     */
    public GameStateSyncPayload withRoom(long hostId, int maxPlayers) {
        GameSnapshot s = gameState;
        return new GameStateSyncPayload(new GameSnapshot(
                s.roomId(), hostId, maxPlayers, s.turnNumber(), s.roundNumber(), s.currentPlayerId(),
                s.welfareFund(), s.phase(), s.isDouble(), s.consecutiveDoubleCount(),
                s.playerOrder(), s.playerStates(), s.propertyStates(), s.config()));
    }

    /**
     * 게임 상태 전체.
     * phase는 서버가 기다리는 입력(TurnPhase 이름)이고, 클라는 이 값으로 띄울 선택창을 정한다.
     * hostId는 방장의 playerId, maxPlayers는 방을 만들 때 정한 최대 인원이다. (지금 인원이 아니다)
     * 방 정보를 읽지 못하면 두 필드는 JSON에서 빠진다.
     * playerOrder는 턴 순서, config는 이 게임의 규칙 설정(초기 자금, 월급, 세금, 최대 라운드, 무인도 영업정지 턴, 맵 번호)이다.
     */
    public record GameSnapshot(
            String roomId,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long hostId,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer maxPlayers,
            int turnNumber,
            int roundNumber,
            long currentPlayerId,
            long welfareFund,
            String phase,
            @JsonProperty("isDouble") boolean isDouble,
            int consecutiveDoubleCount,
            List<Long> playerOrder,
            List<PlayerSnapshot> playerStates,
            List<PropertySnapshot> propertyStates,
            GameConfig config) { }

    /**
     * 플레이어 한 명. finalRank 0은 아직 등수가 정해지지 않음이다.
     * portraitId는 게임을 시작할 때 저장돼 있던 초상화 번호(0~7)다. 고르지 않았거나 봇이면 이 필드는 빠진다.
     * bot은 봇이면 true다. 재접속한 유저가 상대 초상화와 봇을 복원하는 데 쓴다.
     */
    public record PlayerSnapshot(
            long playerId,
            String nickname,
            int position,
            long money,
            int islandTurnsRemaining,
            @JsonProperty("isBankrupt") boolean isBankrupt,
            int finalRank,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer portraitId,
            boolean bot) { }

    /** 땅 한 곳. ownerId가 null이면 주인 없음이고, buildingLevel은 0(땅) ~ 3(호텔)이다. */
    public record PropertySnapshot(int propertyId, Long ownerId, int buildingLevel){}
}

