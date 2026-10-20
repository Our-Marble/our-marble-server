package com.sparta.ourmarbleserver.config;

/**
 * Redoc 문서에 보여 주는 예시 JSON 모음. 서버가 실제로 보내는 형식(type 포함)이다.
 * 요청 페이지와 알림 페이지가 같은 예시를 쓰도록 한곳에 모았다. 오류 예시는 각 오류 코드 enum의 code, message와 같아야 한다.
 */
public final class DocExamples {

    private DocExamples() {
    }

    public static final String CONNECTED = "{\"type\": \"CONNECTED\", \"playerId\": 7, \"nickname\": \"Alice\"}";
    public static final String ROOM_ENTERED = "{\"type\": \"ROOM_ENTERED\"}";
    public static final String ROOM_LEFT = "{\"type\": \"ROOM_LEFT\"}";
    public static final String ROOM_LIST = "{\"type\": \"ROOM_LIST\", \"rooms\": [{\"roomId\": \"r_3\", \"hostNickname\": \"철수\", \"mapId\": 1, \"playerCount\": 2, \"maxPlayers\": 4}]}";
    public static final String PLAYER_RECORD = "{\"type\": \"PLAYER_RECORD\", \"nickname\": \"Alice\", \"gamesPlayed\": 10, \"gamesWon\": 4}";
    public static final String ROOM_UPDATED = "{\"type\": \"ROOM_UPDATED\", \"room\": {\"roomId\": \"r_3\", \"hostId\": 1, \"mapId\": 1, \"status\": \"WAITING\", \"config\": {\"startMoney\": 500000, \"salaryAmount\": 100000, \"taxAmount\": 100000, \"maxRound\": 30, \"islandTurns\": 3, \"mapId\": 1, \"limitTimeSeconds\": 60}, \"players\": [{\"playerId\": 1, \"nickname\": \"Alice\", \"ready\": true, \"bot\": false}, {\"playerId\": 2, \"nickname\": \"Bob\", \"ready\": false, \"bot\": false}, {\"playerId\": -1, \"nickname\": \"봇1\", \"ready\": true, \"bot\": true}], \"maxPlayers\": 4}}";
    public static final String GAME_STARTED = "{\"type\": \"GAME_STARTED\", \"roomId\": \"r_3\"}";
    public static final String DICE_ROLLED = "{\"type\": \"DICE_ROLLED\", \"playerId\": 2, \"dice1\": 3, \"dice2\": 4}";
    public static final String CARD_DRAWN = "{\"type\": \"CARD_DRAWN\", \"playerId\": 1, \"cardId\": 5}";
    public static final String DESTINATION_CHOSEN = "{\"type\": \"DESTINATION_CHOSEN\", \"playerId\": 2, \"destinationPosition\": 31}";
    public static final String PROPERTY_PURCHASED = "{\"type\": \"PROPERTY_PURCHASED\", \"playerId\": 3, \"propertyId\": 104, \"isAccept\": true}";
    public static final String BUILT = "{\"type\": \"BUILT\", \"playerId\": 1, \"propertyId\": 104, \"isAccept\": true}";
    public static final String PROPERTY_ACQUIRED = "{\"type\": \"PROPERTY_ACQUIRED\", \"playerId\": 3, \"propertyId\": 104, \"isAccept\": true}";
    public static final String PROPERTIES_SOLD = "{\"type\": \"PROPERTIES_SOLD\", \"playerId\": 3, \"propertyIds\": [104, 105]}";
    public static final String GAME_STATE_SYNC = "{\"type\": \"GAME_STATE_SYNC\", \"gameState\": {\"roomId\": \"r_3\", \"hostId\": 1, \"maxPlayers\": 4, \"turnNumber\": 12, \"roundNumber\": 3, \"currentPlayerId\": 2, \"welfareFund\": 100000, \"phase\": \"AWAITING_ROLL\", \"isDouble\": false, \"consecutiveDoubleCount\": 0, \"playerOrder\": [1, 2], \"playerStates\": [{\"playerId\": 1, \"nickname\": \"Alice\", \"position\": 8, \"money\": 420000, \"islandTurnsRemaining\": 0, \"isBankrupt\": false, \"finalRank\": 0}, {\"playerId\": 2, \"nickname\": \"Bob\", \"position\": 15, \"money\": 310000, \"islandTurnsRemaining\": 0, \"isBankrupt\": false, \"finalRank\": 0}], \"propertyStates\": [{\"propertyId\": 104, \"ownerId\": 1, \"buildingLevel\": 2}, {\"propertyId\": 105, \"ownerId\": 2, \"buildingLevel\": 0}, {\"propertyId\": 106, \"ownerId\": null, \"buildingLevel\": 0}], \"config\": {\"startMoney\": 500000, \"salaryAmount\": 100000, \"taxAmount\": 100000, \"maxRound\": 30, \"islandTurns\": 3, \"mapId\": 1, \"limitTimeSeconds\": 60}}}";
    public static final String CHAT_SEND = "{\"type\": \"CHAT_SEND\", \"content\": \"하이\"}";
    public static final String CHAT_BROADCAST = "{\"type\": \"CHAT_BROADCAST\", \"playerId\": 1, \"content\": \"하이\"}";
    public static final String COMMON_INVALID_MESSAGE = "{\"type\": \"COMMON_ERROR\", \"code\": \"INVALID_MESSAGE\", \"message\": \"메시지 형식이 올바르지 않습니다.\"}";
    public static final String LOBBY_INVALID_STATE = "{\"type\": \"LOBBY_ERROR\", \"code\": \"INVALID_STATE\", \"message\": \"지금은 할 수 없는 요청입니다.\"}";
    public static final String LOBBY_ROOM_NOT_FOUND = "{\"type\": \"LOBBY_ERROR\", \"code\": \"ROOM_NOT_FOUND\", \"message\": \"방을 찾을 수 없습니다.\"}";
    public static final String LOBBY_ROOM_FULL = "{\"type\": \"LOBBY_ERROR\", \"code\": \"ROOM_FULL\", \"message\": \"방이 가득 찼습니다.\"}";
    public static final String LOBBY_ALREADY_IN_ROOM = "{\"type\": \"LOBBY_ERROR\", \"code\": \"ALREADY_IN_ROOM\", \"message\": \"이미 방에 참가 중입니다.\"}";
    public static final String LOBBY_NOT_IN_ROOM = "{\"type\": \"LOBBY_ERROR\", \"code\": \"NOT_IN_ROOM\", \"message\": \"방에 참가하지 않았습니다.\"}";
    public static final String LOBBY_NOT_HOST = "{\"type\": \"LOBBY_ERROR\", \"code\": \"NOT_HOST\", \"message\": \"방장만 할 수 있습니다.\"}";
    public static final String LOBBY_ROOM_ALREADY_STARTED = "{\"type\": \"LOBBY_ERROR\", \"code\": \"ROOM_ALREADY_STARTED\", \"message\": \"이미 시작한 방입니다.\"}";
    public static final String LOBBY_NOT_ENOUGH_PLAYERS = "{\"type\": \"LOBBY_ERROR\", \"code\": \"NOT_ENOUGH_PLAYERS\", \"message\": \"최소 2명이 필요합니다.\"}";
    public static final String LOBBY_NOT_ALL_READY = "{\"type\": \"LOBBY_ERROR\", \"code\": \"NOT_ALL_READY\", \"message\": \"모두 준비해야 시작할 수 있습니다.\"}";
    public static final String LOBBY_INVALID_MAX_PLAYERS = "{\"type\": \"LOBBY_ERROR\", \"code\": \"INVALID_MAX_PLAYERS\", \"message\": \"최대 인원은 2~4명이고, 지금 방 사람 수보다 적을 수 없습니다.\"}";
    public static final String LOBBY_INVALID_BOT_COUNT = "{\"type\": \"LOBBY_ERROR\", \"code\": \"INVALID_BOT_COUNT\", \"message\": \"봇 수는 0 이상이고, 사람과 봇을 합쳐 최대 인원 이하여야 합니다.\"}";
    public static final String LOBBY_ROOM_BUSY = "{\"type\": \"LOBBY_ERROR\", \"code\": \"ROOM_BUSY\", \"message\": \"방이 다른 요청을 처리하고 있습니다. 잠시 후 다시 시도해 주세요.\"}";
    public static final String GAME_INVALID_STATE = "{\"type\": \"GAME_ERROR\", \"code\": \"INVALID_STATE\", \"message\": \"지금은 할 수 없는 요청입니다.\"}";
    public static final String GAME_NOT_YOUR_TURN = "{\"type\": \"GAME_ERROR\", \"code\": \"NOT_YOUR_TURN\", \"message\": \"내 차례가 아닙니다.\"}";
    public static final String GAME_NOT_ENOUGH_MONEY = "{\"type\": \"GAME_ERROR\", \"code\": \"NOT_ENOUGH_MONEY\", \"message\": \"현금이 부족합니다.\"}";
    public static final String GAME_INVALID_PROPERTY = "{\"type\": \"GAME_ERROR\", \"code\": \"INVALID_PROPERTY\", \"message\": \"올바르지 않은 땅입니다.\"}";
    public static final String GAME_ALREADY_OWNED = "{\"type\": \"GAME_ERROR\", \"code\": \"ALREADY_OWNED\", \"message\": \"이미 주인이 있는 땅입니다.\"}";
    public static final String GAME_NOT_OWNER = "{\"type\": \"GAME_ERROR\", \"code\": \"NOT_OWNER\", \"message\": \"내 땅이 아닙니다.\"}";
    public static final String GAME_CANNOT_BUILD = "{\"type\": \"GAME_ERROR\", \"code\": \"CANNOT_BUILD\", \"message\": \"건설할 수 없는 땅입니다.\"}";
    public static final String GAME_MAX_LEVEL = "{\"type\": \"GAME_ERROR\", \"code\": \"MAX_LEVEL\", \"message\": \"더 이상 건설할 수 없습니다.\"}";
    public static final String GAME_CANNOT_ACQUIRE = "{\"type\": \"GAME_ERROR\", \"code\": \"CANNOT_ACQUIRE\", \"message\": \"인수할 수 없는 땅입니다.\"}";
    public static final String GAME_NOT_ENOUGH_SELL = "{\"type\": \"GAME_ERROR\", \"code\": \"NOT_ENOUGH_SELL\", \"message\": \"선택한 땅을 팔아도 통행료가 부족합니다.\"}";
    public static final String GAME_PLAYER_BANKRUPT = "{\"type\": \"GAME_ERROR\", \"code\": \"PLAYER_BANKRUPT\", \"message\": \"파산한 플레이어입니다.\"}";
    public static final String GAME_INVALID_PROPERTY_LIST = "{\"type\": \"GAME_ERROR\", \"code\": \"INVALID_PROPERTY_LIST\", \"message\": \"매각할 땅 목록이 올바르지 않습니다.\"}";
}