package com.sparta.ourmarbleserver.game.controller;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
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
import io.swagger.v3.oas.models.Paths;

/**
 * Redoc 문서 전용 컨트롤러. WebSocket 메시지는 REST 엔드포인트가 없어서,
 * 서버가 방 전원에게 보내는 알림의 payload 모양과 예시 값을 문서에 보여 주려고 만들었다.
 * 필드 이름과 타입은 payload dto에서 자동으로 읽고, 예시 값은 @ExampleObject에 적은 값이다.
 * 실제 게임 로직과 무관하다.
 */
@Tag(name = "서버 → 클라 알림", description = "WebSocket으로 방 전원에게 전송. 메시지 봉투는 { type, roomId, seq, payload } 이고, 여기에는 payload 모양만 나온다.")
@RestController
@RequestMapping("/docs/notifications")
public class MessageDocController {

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

    /** 문서에 나오는 순서 (명세 표 순서). Redoc은 이 순서 그대로 보여 준다. */
    private static final List<String> DOC_ORDER = List.of(
            "/docs/notifications/built",
            "/docs/notifications/card-drawn",
            "/docs/notifications/destination-chosen",
            "/docs/notifications/dice-rolled",
            "/docs/notifications/properties-sold",
            "/docs/notifications/property-acquired",
            "/docs/notifications/property-purchased");

    /** springdoc이 주소를 정해진 순서 없이 내보내서, 위 순서로 다시 배열한다. 목록에 없는 주소는 뒤에 붙인다. */
    @Bean
    public OpenApiCustomizer notificationOrderCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            Paths ordered = new Paths();
            DOC_ORDER.forEach(path -> {
                if (openApi.getPaths().containsKey(path)) {
                    ordered.addPathItem(path, openApi.getPaths().get(path));
                }
            });
            openApi.getPaths().forEach((path, item) -> {
                if (!ordered.containsKey(path)) {
                    ordered.addPathItem(path, item);
                }
            });
            openApi.setPaths(ordered);
        };
    }
}