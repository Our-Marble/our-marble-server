package com.sparta.ourmarbleserver.game.controller;

import java.util.List;

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
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Redoc 문서 전용 컨트롤러. WebSocket 메시지는 REST 엔드포인트가 없어서,
 * 서버가 방 전원에게 보내는 알림의 payload 모양을 문서에 보여 주려고 만들었다.
 * 실제 게임 로직과 무관하며, 응답은 예시 값이다.
 */
@Tag(name = "서버 → 클라 알림", description = "WebSocket으로 방 전원에게 전송. 메시지 봉투는 { type, roomId, seq, payload } 이고, 여기에는 payload 모양만 나온다.")
@RestController
@RequestMapping("/docs/notifications")
public class MessageDocController {

    @Operation(summary = "ROLL_DICE", description = "주사위 결과")
    @GetMapping("/roll-dice")
    public DiceRolledPayload rollDice() {
        return new DiceRolledPayload(1, 3, 4);
    }

    @Operation(summary = "CHOOSE_DESTINATION → DESTINATION_CHOSEN", description = "세계여행 목적지 선택 결과")
    @GetMapping("/destination-chosen")
    public DestinationChosenPayload destinationChosen() {
        return new DestinationChosenPayload(1, 16);
    }

    @Operation(summary = "DRAW_CARD", description = "황금열쇠 카드 뽑기 결과")
    @GetMapping("/draw-card")
    public CardDrawnPayload drawCard() {
        return new CardDrawnPayload(1, 3);
    }

    @Operation(summary = "PROPERTY_PURCHASED", description = "땅 구매 결과. 거절이면 isAccept=false")
    @GetMapping("/property-purchased")
    public PropertyPurchasedPayload propertyPurchased() {
        return new PropertyPurchasedPayload(1, 101, true);
    }

    @Operation(summary = "BUILD", description = "건설 결과. 거절이면 isAccept=false")
    @GetMapping("/build")
    public BuiltPayload build() {
        return new BuiltPayload(1, 101, true);
    }

    @Operation(summary = "PROPERTY_ACQUIRED", description = "인수 결과. 거절이면 isAccept=false")
    @GetMapping("/property-acquired")
    public PropertyAcquiredPayload propertyAcquired() {
        return new PropertyAcquiredPayload(2, 103, true);
    }

    @Operation(summary = "PROPERTIES_SOLD", description = "매각 결과")
    @GetMapping("/properties-sold")
    public PropertiesSoldPayload propertiesSold() {
        return new PropertiesSoldPayload(2, List.of(104, 105));
    }
}