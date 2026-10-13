package com.sparta.ourmarbleserver.game.controller;

import java.util.List;

import com.sparta.ourmarbleserver.game.dto.GameStateSyncPayload;
import com.sparta.ourmarbleserver.game.state.GameConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sparta.ourmarbleserver.card.dto.CardDrawnPayload;
import com.sparta.ourmarbleserver.game.dto.DestinationChosenPayload;
import com.sparta.ourmarbleserver.game.dto.DiceRolledPayload;
import com.sparta.ourmarbleserver.property.dto.BuiltPayload;
import com.sparta.ourmarbleserver.property.dto.PropertiesSoldPayload;
import com.sparta.ourmarbleserver.property.dto.PropertyAcquiredPayload;
import com.sparta.ourmarbleserver.property.dto.PropertyPurchasedPayload;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Redoc 문서 전용 컨트롤러. WebSocket 메시지는 REST 엔드포인트가 없어서,
 * 서버가 방 전원에게 보내는 알림의 payload 모양과 예시 값을 문서에 보여 주려고 만들었다.
 * 필드 이름과 타입은 payload dto에서 자동으로 읽고, 예시 값은 @ExampleObject에 적은 값이다.
 * 실제 게임 로직과 무관하다.
 */
@Tag(name = "게임 알림", description = "서버가 WebSocket으로 방 전원에게 보내는 게임 알림")
@RestController
@RequestMapping("/docs/notifications")
public class GameMessageDocController {

    @Operation(summary = "건설", description = "요청 BUILD → 알림 BUILT. 건설 결과. 거절이면 isAccept=false [예시 JSON 열기](/docs/notifications/built)")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BuiltPayload.class),
                    examples = @ExampleObject(value = "{\"playerId\": 1, \"propertyId\": 104, \"isAccept\": true}")))
    @GetMapping("/built")
    public BuiltPayload built() {
        return new BuiltPayload(1, 104, true);
    }

    @Operation(summary = "카드뽑기", description = "요청 DRAW_CARD → 알림 CARD_DRAWN. 황금열쇠 카드 뽑기 결과 [예시 JSON 열기](/docs/notifications/card-drawn)")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CardDrawnPayload.class),
                    examples = @ExampleObject(value = "{\"playerId\": 1, \"cardId\": 5}")))
    @GetMapping("/card-drawn")
    public CardDrawnPayload cardDrawn() {
        return new CardDrawnPayload(1, 5);
    }

    @Operation(summary = "여행지선택", description = "요청 CHOOSE_DESTINATION → 알림 DESTINATION_CHOSEN. 세계여행 목적지 선택 결과 [예시 JSON 열기](/docs/notifications/destination-chosen)")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DestinationChosenPayload.class),
                    examples = @ExampleObject(value = "{\"playerId\": 2, \"destinationPosition\": 31}")))
    @GetMapping("/destination-chosen")
    public DestinationChosenPayload destinationChosen() {
        return new DestinationChosenPayload(2, 31);
    }

    @Operation(summary = "주사위굴리기", description = "요청 ROLL_DICE → 알림 DICE_ROLLED. 주사위 결과 [예시 JSON 열기](/docs/notifications/dice-rolled)")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DiceRolledPayload.class),
                    examples = @ExampleObject(value = "{\"playerId\": 2, \"dice1\": 3, \"dice2\": 4}")))
    @GetMapping("/dice-rolled")
    public DiceRolledPayload diceRolled() {
        return new DiceRolledPayload(2, 3, 4);
    }

    @Operation(summary = "게임상태동기화", description = "요청 GAME_STATE_REQUEST → 알림 GAME_STATE_SYNC. 재접속한 요청자에게만 현재 게임 상태 전체를 보낸다. phase는 서버가 기다리는 입력(TurnPhase 이름), finalRank 0은 등수 미정, buildingLevel은 0(땅)~3(호텔), ownerId가 null이면 주인 없음 [예시 JSON 열기](/docs/notifications/game-state-sync)")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = GameStateSyncPayload.class),
                    examples = @ExampleObject(value = "{\"gameState\": {\"roomId\": \"r_3\", \"turnNumber\": 12, \"roundNumber\": 3, \"currentPlayerId\": 2, \"welfareFund\": 100000, \"phase\": \"AWAITING_ROLL\", \"isDouble\": false, \"consecutiveDoubleCount\": 0, \"playerOrder\": [1, 2], \"playerStates\": [{\"playerId\": 1, \"nickname\": \"Alice\", \"position\": 8, \"money\": 420000, \"islandTurnsRemaining\": 0, \"isBankrupt\": false, \"finalRank\": 0}, {\"playerId\": 2, \"nickname\": \"Bob\", \"position\": 15, \"money\": 310000, \"islandTurnsRemaining\": 0, \"isBankrupt\": false, \"finalRank\": 0}], \"propertyStates\": [{\"propertyId\": 104, \"ownerId\": 1, \"buildingLevel\": 2}, {\"propertyId\": 105, \"ownerId\": 2, \"buildingLevel\": 0}, {\"propertyId\": 106, \"ownerId\": null, \"buildingLevel\": 0}], \"config\": {\"startMoney\": 500000, \"salaryAmount\": 100000, \"taxAmount\": 100000, \"maxRound\": 30, \"islandTurns\": 3, \"mapId\": 1, \"limitTimeSeconds\": 20}}}")))
    @GetMapping("/game-state-sync")
    public GameStateSyncPayload gameStateSync() {
        return new GameStateSyncPayload(new GameStateSyncPayload.GameSnapshot(
                "r_3", 12, 3, 2L, 100000L, "AWAITING_ROLL", false, 0,
                List.of(1L, 2L),
                List.of(new GameStateSyncPayload.PlayerSnapshot(1L, "Alice", 8, 420000L, 0, false, 0),
                        new GameStateSyncPayload.PlayerSnapshot(2L, "Bob", 15, 310000L, 0, false, 0)),
                List.of(new GameStateSyncPayload.PropertySnapshot(104, 1L, 2),
                        new GameStateSyncPayload.PropertySnapshot(105, 2L, 0),
                        new GameStateSyncPayload.PropertySnapshot(106, null, 0)),
                new GameConfig(500000L, 100000L, 100000L, 30, 3, 1, 20)));
    }

    @Operation(summary = "자산매각", description = "요청 SELL_PROPERTIES → 알림 PROPERTIES_SOLD. 매각 결과 [예시 JSON 열기](/docs/notifications/properties-sold)")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PropertiesSoldPayload.class),
                    examples = @ExampleObject(value = "{\"playerId\": 3, \"propertyIds\": [104, 105]}")))
    @GetMapping("/properties-sold")
    public PropertiesSoldPayload propertiesSold() {
        return new PropertiesSoldPayload(3, List.of(104, 105));
    }

    @Operation(summary = "자산인수", description = "요청 ACQUIRE_PROPERTY → 알림 PROPERTY_ACQUIRED. 인수 결과. 거절이면 isAccept=false [예시 JSON 열기](/docs/notifications/property-acquired)")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PropertyAcquiredPayload.class),
                    examples = @ExampleObject(value = "{\"playerId\": 3, \"propertyId\": 104, \"isAccept\": true}")))
    @GetMapping("/property-acquired")
    public PropertyAcquiredPayload propertyAcquired() {
        return new PropertyAcquiredPayload(3, 104, true);
    }

    @Operation(summary = "땅구매", description = "요청 PURCHASE_PROPERTY → 알림 PROPERTY_PURCHASED. 땅 구매 결과. 거절이면 isAccept=false [예시 JSON 열기](/docs/notifications/property-purchased)")
    @ApiResponse(responseCode = "200", description = "payload",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PropertyPurchasedPayload.class),
                    examples = @ExampleObject(value = "{\"playerId\": 3, \"propertyId\": 104, \"isAccept\": true}")))
    @GetMapping("/property-purchased")
    public PropertyPurchasedPayload propertyPurchased() {
        return new PropertyPurchasedPayload(3, 104, true);
    }

}