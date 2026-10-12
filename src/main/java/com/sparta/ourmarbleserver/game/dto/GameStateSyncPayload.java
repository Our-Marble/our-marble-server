package com.sparta.ourmarbleserver.game.dto;

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
 * 봇 여부는 서버에서만 쓰는 값이라 보내지 않는다.
 */
public record GameStateSyncPayload(GameSnapshot gameState) {

    /** 시작한 게임 상태에서 알림 내용을 만든다. */
    public static GameStateSyncPayload from(GameState state) {
        List<PlayerSnapshot> players = state.players().stream()
                .map(player -> new PlayerSnapshot(player.getPlayerId(), player.getNickname(), player.getPosition(),
                        player.getMoney(), player.getIslandTurnsRemaining(), player.isBankrupt(), player.getFinalRank()))
                .toList();
        List<PropertySnapshot> properties = state.properties().stream()
                .map(property -> new PropertySnapshot(property.getPropertyId(), property.getOwnerId(),
                        property.getBuildingLevel().index()))
                .toList();
        return new GameStateSyncPayload(new GameSnapshot(
                state.getRoomId(), state.getTurnNumber(), state.getRoundNumber(), state.getCurrentPlayerId(),
                state.getWelfareFund(), state.getPhase().name(), state.isDouble(), state.getConsecutiveDoubleCount(),
                state.playerOrder(), players, properties, state.getConfig()));
    }

    /**
     * 게임 상태 전체.
     * phase는 서버가 기다리는 입력(TurnPhase 이름)이고, 클라는 이 값으로 띄울 선택창을 정한다.
     * playerOrder는 턴 순서, config는 이 게임의 규칙 설정(초기 자금, 월급, 세금, 최대 라운드, 무인도 영업정지 턴, 맵 번호)이다.
     */
    public record GameSnapshot(
            String roomId,
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

    /** 플레이어 한 명. finalRank 0은 아직 등수가 정해지지 않음이다. */
    public record PlayerSnapshot(
            long playerId,
            String nickname,
            int position,
            long money,
            int islandTurnsRemaining,
            @JsonProperty("isBankrupt") boolean isBankrupt,
            int finalRank) { }

    /** 땅 한 곳. ownerId가 null이면 주인 없음이고, buildingLevel은 0(땅) ~ 3(호텔)이다. */
    public record PropertySnapshot(int propertyId, Long ownerId, int buildingLevel){}
}

