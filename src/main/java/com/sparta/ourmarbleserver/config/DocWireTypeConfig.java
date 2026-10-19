package com.sparta.ourmarbleserver.config;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.sparta.ourmarbleserver.global.protocol.MessageType;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

/**
 * Redoc 문서에 실제 전송 형식(type 포함)이 나오도록 문서의 스키마에 type을 더한다.
 * payload record에는 type이 없고(MessageCodec이 보낼 때 붙임), 문서만 { "type": ..., payload 필드... } 모양으로 보여 준다.
 * 문서 주소 /docs/requests/create-room, /docs/notifications/room-updated 처럼 주소가 type 이름(소문자, 하이픈)과 같아야 한다.
 * MessageType에 없는 주소는 건드리지 않는다.
 * 요청 페이지의 200 응답은 RESULT_TYPE에 적힌 결과 알림의 type도 보여 준다.
 */
@Configuration
public class DocWireTypeConfig {

    private static final String REQUESTS = "/docs/requests/";
    private static final String NOTIFICATIONS = "/docs/notifications/";
    private static final String JSON = "application/json";
    /** 요청 type → 그 요청의 200 응답에 보여 줄 결과 알림 type. 한 가지 알림 스키마를 보여 주는 요청만 적는다. */
    private static final Map<String, String> RESULT_TYPE = Map.ofEntries(
            Map.entry("ROLL_DICE", "DICE_ROLLED"),
            Map.entry("DRAW_CARD", "CARD_DRAWN"),
            Map.entry("CHOOSE_DESTINATION", "DESTINATION_CHOSEN"),
            Map.entry("PURCHASE_PROPERTY", "PROPERTY_PURCHASED"),
            Map.entry("BUILD", "BUILT"),
            Map.entry("ACQUIRE_PROPERTY", "PROPERTY_ACQUIRED"),
            Map.entry("SELL_PROPERTIES", "PROPERTIES_SOLD"),
            Map.entry("GAME_STATE_REQUEST", "GAME_STATE_SYNC"),
            Map.entry("ROOM_LIST_REQUEST", "ROOM_LIST"),
            Map.entry("START_GAME", "GAME_STARTED"),
            Map.entry("CHAT_SEND", "CHAT_BROADCAST"));

    @Bean
    public OpenApiCustomizer wireTypeCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> {
                if (path.startsWith(REQUESTS) && item.getPost() != null) {
                    String type = typeOf(path, REQUESTS);
                    if (type != null) {
                        addToRequest(item.getPost(), type);
                        String resultType = RESULT_TYPE.get(type);
                        if (resultType != null) {
                            addToResult(item.getPost(), resultType);
                        }
                    }
                } else if (path.startsWith(NOTIFICATIONS) && item.getGet() != null) {
                    String type = typeOf(path, NOTIFICATIONS);
                    if (type != null) {
                        addToResponse(item.getGet(), type);
                    }
                }
            });
        };
    }

    /** 주소에서 type 이름을 만든다. MessageType에 없는 이름이면 null. */
    private static String typeOf(String path, String prefix) {
        String name = path.substring(prefix.length()).toUpperCase(Locale.ROOT).replace('-', '_');
        for (MessageType type : MessageType.values()) {
            if (type.name().equals(name)) {
                return name;
            }
        }
        return null;
    }

    private static void addToRequest(Operation operation, String type) {
        RequestBody body = operation.getRequestBody();
        if (body == null) {
            body = new RequestBody();
            operation.setRequestBody(body);
        }
        body.setContent(withType(body.getContent(), type));
    }

    /** 요청 페이지의 200 응답 스키마에 결과 알림의 type을 더한다. 200 응답이 없으면 아무것도 하지 않는다. */
    private static void addToResult(Operation operation, String resultType) {
        ApiResponses responses = operation.getResponses();
        ApiResponse ok = responses == null ? null : responses.get("200");
        if (ok != null) {
            ok.setContent(withType(ok.getContent(), resultType));
        }
    }

    private static void addToResponse(Operation operation, String type) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        ApiResponse ok = responses.get("200");
        if (ok == null) {
            ok = new ApiResponse().description("payload");
            responses.addApiResponse("200", ok);
        }
        ok.setContent(withType(ok.getContent(), type));
    }

    /** 본문이 없으면 type만 있는 스키마를 만들고, 있으면 type과 기존 스키마를 allOf로 합친다. */
    private static Content withType(Content content, String type) {
        Content result = content == null ? new Content() : content;
        if (result.isEmpty()) {
            result.addMediaType(JSON, new MediaType().schema(typeOnly(type)));
            return result;
        }
        result.values().forEach(mediaType -> {
            Schema<?> payload = mediaType.getSchema();
            if (payload == null) {
                mediaType.setSchema(typeOnly(type));
            } else {
                Schema<Object> all = new Schema<>();
                all.addAllOfItem(typeOnly(type));
                all.addAllOfItem(payload);
                mediaType.setSchema(all);
            }
        });
        return result;
    }

    /** { "type": "<type>" } 스키마. OpenAPI 3.0과 3.1 둘 다에서 type이 나오도록 type과 types를 같이 채운다. */
    private static Schema<Object> typeOnly(String type) {
        Schema<Object> typeField = new Schema<>();
        typeField.setType("string");
        typeField.setTypes(Set.of("string"));
        typeField.setDescription("메시지 종류");
        typeField.addEnumItemObject(type);
        typeField.setExample(type);

        Schema<Object> object = new Schema<>();
        object.setType("object");
        object.setTypes(Set.of("object"));
        object.addProperty("type", typeField);
        object.addRequiredItem("type");
        return object;
    }
}