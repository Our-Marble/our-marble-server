package com.sparta.ourmarbleserver.game.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sparta.ourmarbleserver.card.dto.CardDrawnPayload;
import com.sparta.ourmarbleserver.config.DocExamples;
import com.sparta.ourmarbleserver.game.dto.ChooseDestinationRequestPayload;
import com.sparta.ourmarbleserver.game.dto.DestinationChosenPayload;
import com.sparta.ourmarbleserver.game.dto.DiceRolledPayload;
import com.sparta.ourmarbleserver.game.dto.GameStateSyncPayload;
import com.sparta.ourmarbleserver.global.protocol.ErrorPayload;
import com.sparta.ourmarbleserver.property.dto.AcquirePropertyRequestPayload;
import com.sparta.ourmarbleserver.property.dto.BuildRequestPayload;
import com.sparta.ourmarbleserver.property.dto.BuiltPayload;
import com.sparta.ourmarbleserver.property.dto.PropertiesSoldPayload;
import com.sparta.ourmarbleserver.property.dto.PropertyAcquiredPayload;
import com.sparta.ourmarbleserver.property.dto.PropertyPurchasedPayload;
import com.sparta.ourmarbleserver.property.dto.PurchasePropertyRequestPayload;
import com.sparta.ourmarbleserver.property.dto.SellPropertiesRequestPayload;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Redoc 문서 전용 컨트롤러. 게임이 시작된 뒤(주사위, 구매, 건설 등)에 쓰는 요청과 알림을 문서에 보여 주려고 만들었다.
 * 한 페이지에 요청(오른쪽 위 예시)과 그 결과 알림·오류(응답 예시)를 함께 보여 준다. 요청 본문과 알림 필드는 각 dto에서 자동으로 읽고,
 * 예시 JSON은 DocExamples에 있다. 실제 로직과 무관하고, 호출해도 아무 일도 일어나지 않는다.
 */
@Tag(name = "인게임", description = "게임이 시작된 뒤(주사위, 구매, 건설 등) 클라이언트와 서버가 WebSocket으로 주고받는 메시지")
@RestController
@RequestMapping("/docs")
public class GameDocController {

    @Operation(summary = "주사위굴리기", description = "type: ROLL_DICE. 내 차례에 주사위를 굴린다. 요청 본문은 없다. 결과는 DICE_ROLLED 알림으로 간다.")
    @ApiResponse(responseCode = "200", description = "**DICE_ROLLED** — 주사위 결과. 방 전원에게 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DiceRolledPayload.class),
                    examples = @ExampleObject(name = "DICE_ROLLED", summary = "DICE_ROLLED", value = DocExamples.DICE_ROLLED)))
    @ApiResponse(responseCode = "400", description = "거절되면 **GAME_ERROR**가 요청자에게만 가고 서버 상태는 바뀌지 않는다. 가능한 오류 코드는 오른쪽 예시에서 고를 수 있다. 메시지 형식이 틀리면 COMMON_ERROR(INVALID_MESSAGE)가 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = {
                            @ExampleObject(name = "INVALID_STATE", summary = "INVALID_STATE", value = DocExamples.GAME_INVALID_STATE),
                            @ExampleObject(name = "NOT_YOUR_TURN", summary = "NOT_YOUR_TURN", value = DocExamples.GAME_NOT_YOUR_TURN),
                            @ExampleObject(name = "INVALID_MESSAGE", summary = "INVALID_MESSAGE", value = DocExamples.COMMON_INVALID_MESSAGE)}))
    @PostMapping("/requests/roll-dice")
    public void rollDice() {
    }

    @Operation(summary = "카드뽑기", description = "type: DRAW_CARD. 황금열쇠 칸에 도착했을 때 카드를 뽑는다. 요청 본문은 없다. 결과는 CARD_DRAWN 알림으로 간다.")
    @ApiResponse(responseCode = "200", description = "**CARD_DRAWN** — 황금열쇠 카드 뽑기 결과. 방 전원에게 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CardDrawnPayload.class),
                    examples = @ExampleObject(name = "CARD_DRAWN", summary = "CARD_DRAWN", value = DocExamples.CARD_DRAWN)))
    @ApiResponse(responseCode = "400", description = "거절되면 **GAME_ERROR**가 요청자에게만 가고 서버 상태는 바뀌지 않는다. 가능한 오류 코드는 오른쪽 예시에서 고를 수 있다. 메시지 형식이 틀리면 COMMON_ERROR(INVALID_MESSAGE)가 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = {
                            @ExampleObject(name = "INVALID_STATE", summary = "INVALID_STATE", value = DocExamples.GAME_INVALID_STATE),
                            @ExampleObject(name = "NOT_YOUR_TURN", summary = "NOT_YOUR_TURN", value = DocExamples.GAME_NOT_YOUR_TURN),
                            @ExampleObject(name = "INVALID_MESSAGE", summary = "INVALID_MESSAGE", value = DocExamples.COMMON_INVALID_MESSAGE)}))
    @PostMapping("/requests/draw-card")
    public void drawCard() {
    }

    @Operation(summary = "여행지선택", description = "type: CHOOSE_DESTINATION. 세계여행 칸에서 시작한 턴에 목적지를 고른다. 결과는 DESTINATION_CHOSEN 알림으로 간다.")
    @ApiResponse(responseCode = "200", description = "**DESTINATION_CHOSEN** — 세계여행 목적지 선택 결과. 방 전원에게 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DestinationChosenPayload.class),
                    examples = @ExampleObject(name = "DESTINATION_CHOSEN", summary = "DESTINATION_CHOSEN", value = DocExamples.DESTINATION_CHOSEN)))
    @ApiResponse(responseCode = "400", description = "거절되면 **GAME_ERROR**가 요청자에게만 가고 서버 상태는 바뀌지 않는다. 가능한 오류 코드는 오른쪽 예시에서 고를 수 있다. 메시지 형식이 틀리면 COMMON_ERROR(INVALID_MESSAGE)가 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = {
                            @ExampleObject(name = "INVALID_STATE", summary = "INVALID_STATE", value = DocExamples.GAME_INVALID_STATE),
                            @ExampleObject(name = "INVALID_PROPERTY", summary = "INVALID_PROPERTY", value = DocExamples.GAME_INVALID_PROPERTY),
                            @ExampleObject(name = "NOT_YOUR_TURN", summary = "NOT_YOUR_TURN", value = DocExamples.GAME_NOT_YOUR_TURN),
                            @ExampleObject(name = "INVALID_MESSAGE", summary = "INVALID_MESSAGE", value = DocExamples.COMMON_INVALID_MESSAGE)}))
    @PostMapping("/requests/choose-destination")
    public void chooseDestination(@RequestBody ChooseDestinationRequestPayload body) {
    }

    @Operation(summary = "땅구매", description = "type: PURCHASE_PROPERTY. 도착한 빈 땅을 사거나 거절한다. 결과는 PROPERTY_PURCHASED 알림으로 간다.")
    @ApiResponse(responseCode = "200", description = "**PROPERTY_PURCHASED** — 땅 구매 결과. 거절이면 isAccept=false. 방 전원에게 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PropertyPurchasedPayload.class),
                    examples = @ExampleObject(name = "PROPERTY_PURCHASED", summary = "PROPERTY_PURCHASED", value = DocExamples.PROPERTY_PURCHASED)))
    @ApiResponse(responseCode = "400", description = "거절되면 **GAME_ERROR**가 요청자에게만 가고 서버 상태는 바뀌지 않는다. 가능한 오류 코드는 오른쪽 예시에서 고를 수 있다. 메시지 형식이 틀리면 COMMON_ERROR(INVALID_MESSAGE)가 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = {
                            @ExampleObject(name = "INVALID_STATE", summary = "INVALID_STATE", value = DocExamples.GAME_INVALID_STATE),
                            @ExampleObject(name = "INVALID_PROPERTY", summary = "INVALID_PROPERTY", value = DocExamples.GAME_INVALID_PROPERTY),
                            @ExampleObject(name = "NOT_YOUR_TURN", summary = "NOT_YOUR_TURN", value = DocExamples.GAME_NOT_YOUR_TURN),
                            @ExampleObject(name = "ALREADY_OWNED", summary = "ALREADY_OWNED", value = DocExamples.GAME_ALREADY_OWNED),
                            @ExampleObject(name = "NOT_ENOUGH_MONEY", summary = "NOT_ENOUGH_MONEY", value = DocExamples.GAME_NOT_ENOUGH_MONEY),
                            @ExampleObject(name = "INVALID_MESSAGE", summary = "INVALID_MESSAGE", value = DocExamples.COMMON_INVALID_MESSAGE)}))
    @PostMapping("/requests/purchase-property")
    public void purchaseProperty(@RequestBody PurchasePropertyRequestPayload body) {
    }

    @Operation(summary = "건설", description = "type: BUILD. 내 땅에 도착했을 때 한 단계 건설하거나 거절한다. 결과는 BUILT 알림으로 간다.")
    @ApiResponse(responseCode = "200", description = "**BUILT** — 건설 결과. 거절이면 isAccept=false. 방 전원에게 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BuiltPayload.class),
                    examples = @ExampleObject(name = "BUILT", summary = "BUILT", value = DocExamples.BUILT)))
    @ApiResponse(responseCode = "400", description = "거절되면 **GAME_ERROR**가 요청자에게만 가고 서버 상태는 바뀌지 않는다. 가능한 오류 코드는 오른쪽 예시에서 고를 수 있다. 메시지 형식이 틀리면 COMMON_ERROR(INVALID_MESSAGE)가 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = {
                            @ExampleObject(name = "INVALID_STATE", summary = "INVALID_STATE", value = DocExamples.GAME_INVALID_STATE),
                            @ExampleObject(name = "INVALID_PROPERTY", summary = "INVALID_PROPERTY", value = DocExamples.GAME_INVALID_PROPERTY),
                            @ExampleObject(name = "NOT_YOUR_TURN", summary = "NOT_YOUR_TURN", value = DocExamples.GAME_NOT_YOUR_TURN),
                            @ExampleObject(name = "NOT_OWNER", summary = "NOT_OWNER", value = DocExamples.GAME_NOT_OWNER),
                            @ExampleObject(name = "CANNOT_BUILD", summary = "CANNOT_BUILD", value = DocExamples.GAME_CANNOT_BUILD),
                            @ExampleObject(name = "MAX_LEVEL", summary = "MAX_LEVEL", value = DocExamples.GAME_MAX_LEVEL),
                            @ExampleObject(name = "NOT_ENOUGH_MONEY", summary = "NOT_ENOUGH_MONEY", value = DocExamples.GAME_NOT_ENOUGH_MONEY),
                            @ExampleObject(name = "INVALID_MESSAGE", summary = "INVALID_MESSAGE", value = DocExamples.COMMON_INVALID_MESSAGE)}))
    @PostMapping("/requests/build")
    public void build(@RequestBody BuildRequestPayload body) {
    }

    @Operation(summary = "자산인수", description = "type: ACQUIRE_PROPERTY. 통행료를 낸 뒤 남의 땅을 인수하거나 거절한다. 결과는 PROPERTY_ACQUIRED 알림으로 간다.")
    @ApiResponse(responseCode = "200", description = "**PROPERTY_ACQUIRED** — 인수 결과. 거절이면 isAccept=false. 방 전원에게 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PropertyAcquiredPayload.class),
                    examples = @ExampleObject(name = "PROPERTY_ACQUIRED", summary = "PROPERTY_ACQUIRED", value = DocExamples.PROPERTY_ACQUIRED)))
    @ApiResponse(responseCode = "400", description = "거절되면 **GAME_ERROR**가 요청자에게만 가고 서버 상태는 바뀌지 않는다. 가능한 오류 코드는 오른쪽 예시에서 고를 수 있다. 메시지 형식이 틀리면 COMMON_ERROR(INVALID_MESSAGE)가 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = {
                            @ExampleObject(name = "INVALID_STATE", summary = "INVALID_STATE", value = DocExamples.GAME_INVALID_STATE),
                            @ExampleObject(name = "INVALID_PROPERTY", summary = "INVALID_PROPERTY", value = DocExamples.GAME_INVALID_PROPERTY),
                            @ExampleObject(name = "NOT_YOUR_TURN", summary = "NOT_YOUR_TURN", value = DocExamples.GAME_NOT_YOUR_TURN),
                            @ExampleObject(name = "CANNOT_ACQUIRE", summary = "CANNOT_ACQUIRE", value = DocExamples.GAME_CANNOT_ACQUIRE),
                            @ExampleObject(name = "NOT_ENOUGH_MONEY", summary = "NOT_ENOUGH_MONEY", value = DocExamples.GAME_NOT_ENOUGH_MONEY),
                            @ExampleObject(name = "INVALID_MESSAGE", summary = "INVALID_MESSAGE", value = DocExamples.COMMON_INVALID_MESSAGE)}))
    @PostMapping("/requests/acquire-property")
    public void acquireProperty(@RequestBody AcquirePropertyRequestPayload body) {
    }

    @Operation(summary = "자산매각", description = "type: SELL_PROPERTIES. 통행료가 모자랄 때 팔 땅의 목록을 보낸다. 결과는 PROPERTIES_SOLD 알림으로 간다.")
    @ApiResponse(responseCode = "200", description = "**PROPERTIES_SOLD** — 매각 결과. 방 전원에게 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PropertiesSoldPayload.class),
                    examples = @ExampleObject(name = "PROPERTIES_SOLD", summary = "PROPERTIES_SOLD", value = DocExamples.PROPERTIES_SOLD)))
    @ApiResponse(responseCode = "400", description = "거절되면 **GAME_ERROR**가 요청자에게만 가고 서버 상태는 바뀌지 않는다. 가능한 오류 코드는 오른쪽 예시에서 고를 수 있다. 메시지 형식이 틀리면 COMMON_ERROR(INVALID_MESSAGE)가 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = {
                            @ExampleObject(name = "INVALID_STATE", summary = "INVALID_STATE", value = DocExamples.GAME_INVALID_STATE),
                            @ExampleObject(name = "NOT_YOUR_TURN", summary = "NOT_YOUR_TURN", value = DocExamples.GAME_NOT_YOUR_TURN),
                            @ExampleObject(name = "INVALID_PROPERTY_LIST", summary = "INVALID_PROPERTY_LIST", value = DocExamples.GAME_INVALID_PROPERTY_LIST),
                            @ExampleObject(name = "INVALID_PROPERTY", summary = "INVALID_PROPERTY", value = DocExamples.GAME_INVALID_PROPERTY),
                            @ExampleObject(name = "NOT_OWNER", summary = "NOT_OWNER", value = DocExamples.GAME_NOT_OWNER),
                            @ExampleObject(name = "NOT_ENOUGH_SELL", summary = "NOT_ENOUGH_SELL", value = DocExamples.GAME_NOT_ENOUGH_SELL),
                            @ExampleObject(name = "INVALID_MESSAGE", summary = "INVALID_MESSAGE", value = DocExamples.COMMON_INVALID_MESSAGE)}))
    @PostMapping("/requests/sell-properties")
    public void sellProperties(@RequestBody SellPropertiesRequestPayload body) {
    }

    @Operation(summary = "게임상태요청", description = "type: GAME_STATE_REQUEST. 재접속했을 때 현재 게임 상태를 요청한다. 요청 본문은 없다. 결과는 GAME_STATE_SYNC 알림으로 요청자에게만 간다. 재접속한 사람의 AFK 상태(자동 진행)는 이때 풀린다.\n\n"
            + "진행 중인 게임이 없으면(대기실에 있는 사람 등) payload가 없는 GAME_STATE_SYNC가 간다.")
    @ApiResponse(responseCode = "200", description = "**GAME_STATE_SYNC** — 재접속한 요청자에게만 현재 게임 상태 전체를 보낸다. 진행 중인 게임이 없으면 payload 없이 type만 간다. playerStates[]의 portraitId는 게임을 시작할 때 저장돼 있던 초상화 번호(0~7)로 고르지 않았거나 봇이면 빠지고(게임 중에 바꿔도 반영하지 않는다), bot은 봇이면 true다. phase는 서버가 기다리는 입력(TurnPhase 이름), finalRank 0은 등수 미정, buildingLevel은 0(땅)~3(호텔), ownerId가 null이면 주인 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = GameStateSyncPayload.class),
                    examples = @ExampleObject(name = "GAME_STATE_SYNC", summary = "GAME_STATE_SYNC", value = DocExamples.GAME_STATE_SYNC)))
    @ApiResponse(responseCode = "400", description = "이 요청에는 요청 내용 때문에 거절되는 경우가 없다. 메시지 형식이 틀리면 COMMON_ERROR(INVALID_MESSAGE)가 요청자에게만 간다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorPayload.class),
                    examples = @ExampleObject(name = "INVALID_MESSAGE", summary = "INVALID_MESSAGE", value = DocExamples.COMMON_INVALID_MESSAGE)))
    @PostMapping("/requests/game-state-request")
    public void gameStateRequest() {
    }
}