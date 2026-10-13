package com.sparta.ourmarbleserver.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Paths;

/**
 * Redoc 문서 순서 설정. 게임·로비·공통 문서의 주소와 분류가 나오는 순서를 정한다.
 * 새 문서 컨트롤러를 추가하면 아래 DOC_ORDER와 TAG_ORDER에도 추가해야 한다. (목록에 없으면 맨 뒤에 붙는다)
 */
@Configuration
public class DocOrderConfig {

    /** 문서에 나오는 순서 (명세 표 순서). Redoc은 이 순서 그대로 보여 준다. */
    private static final List<String> DOC_ORDER = List.of(
            "/docs/requests/build",
            "/docs/requests/draw-card",
            "/docs/requests/choose-destination",
            "/docs/requests/roll-dice",
            "/docs/requests/sell-properties",
            "/docs/requests/acquire-property",
            "/docs/requests/purchase-property",
            "/docs/requests/game-state-request",
            "/docs/notifications/built",
            "/docs/notifications/card-drawn",
            "/docs/notifications/destination-chosen",
            "/docs/notifications/dice-rolled",
            "/docs/notifications/properties-sold",
            "/docs/notifications/property-acquired",
            "/docs/notifications/property-purchased",
            "/docs/notifications/game-state-sync",
            "/docs/requests/create-room",
            "/docs/requests/enter-room",
            "/docs/requests/leave-room",
            "/docs/requests/set-ready",
            "/docs/requests/start-game",
            "/docs/requests/refresh-room-list",
            "/docs/notifications/connected",
            "/docs/notifications/room-entered",
            "/docs/notifications/room-left",
            "/docs/notifications/room-list",
            "/docs/notifications/room-updated",
            "/docs/notifications/game-started",
            "/docs/notifications/error");

    /** 문서에서 분류가 나오는 순서. 목록에 없는 분류(로그인 등)는 뒤에 붙는다. */
    private static final List<String> TAG_ORDER = List.of("게임 요청", "게임 알림", "로비 요청", "로비 알림", "오류 알림");


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

            if (openApi.getTags() != null) {
                List<io.swagger.v3.oas.models.tags.Tag> tags = new ArrayList<>(openApi.getTags());
                tags.sort(Comparator.comparingInt(tag -> {
                    int index = TAG_ORDER.indexOf(tag.getName());
                    return index < 0 ? TAG_ORDER.size() : index;
                }));
                openApi.setTags(tags);
            }
        };
    }
}