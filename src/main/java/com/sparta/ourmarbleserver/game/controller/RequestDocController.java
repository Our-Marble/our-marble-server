package com.sparta.ourmarbleserver.game.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sparta.ourmarbleserver.game.dto.ChooseDestinationRequestPayload;
import com.sparta.ourmarbleserver.property.dto.AcquirePropertyRequestPayload;
import com.sparta.ourmarbleserver.property.dto.BuildRequestPayload;
import com.sparta.ourmarbleserver.property.dto.PurchasePropertyRequestPayload;
import com.sparta.ourmarbleserver.property.dto.SellPropertiesRequestPayload;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Redoc 문서 전용 컨트롤러. 클라이언트가 WebSocket으로 서버에 보내는 게임 요청의 모양과 예시를 문서에 보여 주려고 만들었다.
 * 요청 본문은 각 요청 dto에서 자동으로 읽는다. 실제 게임 로직과 무관하고, 호출해도 아무 일도 일어나지 않는다.
 */
@Tag(name = "게임 요청", description = "클라이언트가 WebSocket으로 서버에 보내는 게임 요청")
@RestController
@RequestMapping("/docs/requests")
public class RequestDocController {

    private static final String NO_RESPONSE =
            "응답 없음. 성공하면 결과 알림이 방 전원에게 가고, 실패하면 ERROR가 요청자에게만 간다.";

    @Operation(summary = "건설", description = "type: BUILD. 내 땅에 도착했을 때 한 단계 건설하거나 거절한다. 결과는 BUILT 알림으로 간다.")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/build")
    public void build(@RequestBody BuildRequestPayload body) {
    }

    @Operation(summary = "카드뽑기", description = "type: DRAW_CARD. 황금열쇠 칸에 도착했을 때 카드를 뽑는다. 요청 본문은 없다. 결과는 CARD_DRAWN 알림으로 간다.")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/draw-card")
    public void drawCard() {
    }

    @Operation(summary = "여행지선택", description = "type: CHOOSE_DESTINATION. 세계여행 칸에서 시작한 턴에 목적지를 고른다. 결과는 DESTINATION_CHOSEN 알림으로 간다.")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/choose-destination")
    public void chooseDestination(@RequestBody ChooseDestinationRequestPayload body) {
    }

    @Operation(summary = "주사위굴리기", description = "type: ROLL_DICE. 내 차례에 주사위를 굴린다. 요청 본문은 없다. 결과는 DICE_ROLLED 알림으로 간다.")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/roll-dice")
    public void rollDice() {
    }

    @Operation(summary = "자산매각", description = "type: SELL_PROPERTIES. 통행료가 모자랄 때 팔 땅의 목록을 보낸다. 결과는 PROPERTIES_SOLD 알림으로 간다.")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/sell-properties")
    public void sellProperties(@RequestBody SellPropertiesRequestPayload body) {
    }

    @Operation(summary = "자산인수", description = "type: ACQUIRE_PROPERTY. 통행료를 낸 뒤 남의 땅을 인수하거나 거절한다. 결과는 PROPERTY_ACQUIRED 알림으로 간다.")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/acquire-property")
    public void acquireProperty(@RequestBody AcquirePropertyRequestPayload body) {
    }

    @Operation(summary = "땅구매", description = "type: PURCHASE_PROPERTY. 도착한 빈 땅을 사거나 거절한다. 결과는 PROPERTY_PURCHASED 알림으로 간다.")
    @ApiResponse(responseCode = "200", description = NO_RESPONSE)
    @PostMapping("/purchase-property")
    public void purchaseProperty(@RequestBody PurchasePropertyRequestPayload body) {
    }
}