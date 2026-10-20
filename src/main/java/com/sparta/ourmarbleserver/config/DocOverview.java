package com.sparta.ourmarbleserver.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;

/**
 * Redoc 맨 위의 문서 제목·버전과 "한눈에 보기" 구간을 만든다. 접속과 메시지 형식 두 문단, 분류별로 요청과 알림을 모은 짧은 표로 이루어진다. (웹소켓은 type, REST는 HTTP 메서드와 주소)
 * 표는 서버가 뜰 때 문서 데이터(/docs/ 주소)에서 자동으로 만들어서, 메시지를 추가하면 표에도 저절로 들어간다.
 * 분류 순서는 DocOrderConfig가 정렬한 tags를 따르므로, 그 정렬이 끝난 뒤에 불러야 한다.
 */
final class DocOverview {

    private static final String TITLE = "# 한눈에 보기";
    /** Redoc 맨 위에 나오는 문서 제목과 버전. 정하지 않으면 springdoc 기본값("OpenAPI definition", "v0")이 나온다. */
    private static final String API_TITLE = "우리의 마블 서버 API";
    private static final String API_VERSION = "1.0.0";
    /** 200 응답 설명에서 "**ROOM_ENTERED** — …"처럼 굵게 적힌 알림 이름을 찾는다. */
    private static final Pattern RESULT = Pattern.compile("\\*\\*([A-Z][A-Z_]+)\\*\\*");

    private static final String INTRO = TITLE + "\n\n"
            + "**접속**: `POST /auth/login`으로 받은 `accessToken`을 `Authorization: Bearer {accessToken}` 헤더에 담아 `/ws`로 접속한다. "
            + "접속하면 서버가 `CONNECTED`를 먼저 보낸다. 같은 계정으로 다시 접속하면 옛 연결은 `DUPLICATE_LOGIN`(종료 코드 4001)으로 끊긴다.\n\n"
            + "**형식**: 모든 메시지는 `{\"type\": \"...\", 필드...}` 모양의 JSON 하나다. "
            + "요청이 거절되면 요청한 사람에게만 `COMMON_ERROR`, `LOBBY_ERROR`, `GAME_ERROR` 중 하나가 `{\"type\", \"code\", \"message\"}`로 온다.\n\n";

    /** 분류 하나에 모이는 요청 type과 알림 type. */
    private static final class Group {
        final Set<String> requests = new LinkedHashSet<>();
        final Set<String> notifications = new LinkedHashSet<>();
    }

    private DocOverview() {
    }

    /** 문서 설명 맨 위에 "한눈에 보기"를 넣는다. 이미 들어 있으면(같은 문서를 다시 만들 때) 아무것도 하지 않는다. */
    static void apply(OpenAPI openApi) {
        Info info = openApi.getInfo();
        if (info == null) {
            info = new Info();
            openApi.setInfo(info);
        }
        info.setTitle(API_TITLE);
        info.setVersion(API_VERSION);
        String current = info.getDescription();
        if (current != null && current.contains(TITLE)) {
            return;
        }
        String text = INTRO + table(groups(openApi));
        info.setDescription(current == null || current.isBlank() ? text : text + "\n\n" + current);
    }

    /** 분류별로 요청과 알림 type을 모은다. 분류 순서는 openApi.getTags()(DocOrderConfig가 정렬함)를 따른다. */
    private static Map<String, Group> groups(OpenAPI openApi) {
        Map<String, Group> groups = new LinkedHashMap<>();
        if (openApi.getTags() != null) {
            for (Tag tag : openApi.getTags()) {
                groups.put(tag.getName(), new Group());
            }
        }
        if (openApi.getPaths() == null) {
            return groups;
        }
        // 알림이 어느 분류 소속인지: 자기 페이지(GET)가 있으면 그 분류, 없으면 그 알림이 처음 결과로 나온 요청의 분류
        Map<String, String> notificationOwner = new LinkedHashMap<>();
        for (Map.Entry<String, PathItem> entry : openApi.getPaths().entrySet()) {
            if (!isDocPath(entry.getKey()) || entry.getValue().getPost() != null || entry.getValue().getGet() == null) {
                continue;
            }
            String type = typeOf(entry.getKey());
            if (!type.endsWith("_ERROR")) {   // 오류 알림은 표에 넣지 않는다 (오류는 형식 문단에서 설명)
                notificationOwner.put(type, tagOf(entry.getValue().getGet()));
            }
        }
        for (Map.Entry<String, PathItem> entry : openApi.getPaths().entrySet()) {
            if (!isDocPath(entry.getKey()) || entry.getValue().getPost() == null) {
                continue;
            }
            Operation request = entry.getValue().getPost();
            String tag = tagOf(request);
            Group group = groups.computeIfAbsent(tag, name -> new Group());
            group.requests.add(typeOf(entry.getKey()));
            for (String result : resultTypes(request)) {
                notificationOwner.putIfAbsent(result, tag);
            }
        }
        // REST 분류(로그인, 플레이어): 웹소켓 type 대신 HTTP 메서드와 주소를 요청 칸에 그대로 적는다.
        // 분류 목록(openApi.getTags())에 없는 주소는 건너뛴다.
        for (Map.Entry<String, PathItem> entry : openApi.getPaths().entrySet()) {
            if (isDocPath(entry.getKey())) {
                continue;
            }
            entry.getValue().readOperationsMap().forEach((method, operation) -> {
                Group group = groups.get(tagOf(operation));
                if (group != null) {
                    group.requests.add(method + " " + entry.getKey());
                }
            });
        }
        notificationOwner.forEach((type, tag) -> groups.computeIfAbsent(tag, name -> new Group()).notifications.add(type));
        return groups;
    }

    private static boolean isDocPath(String path) {
        return path.startsWith("/docs/");
    }

    private static String typeOf(String path) {
        return path.substring(path.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String tagOf(Operation operation) {
        return operation.getTags() == null || operation.getTags().isEmpty() ? "" : operation.getTags().get(0);
    }

    /** 200 응답 설명에 굵게 적힌 알림 이름을 모은다. */
    private static List<String> resultTypes(Operation operation) {
        List<String> types = new ArrayList<>();
        ApiResponses responses = operation.getResponses();
        ApiResponse ok = responses == null ? null : responses.get("200");
        if (ok == null || ok.getDescription() == null) {
            return types;
        }
        Matcher matcher = RESULT.matcher(ok.getDescription());
        while (matcher.find()) {
            types.add(matcher.group(1));
        }
        return types;
    }

    private static String table(Map<String, Group> groups) {
        StringBuilder sb = new StringBuilder("| 분류 | 요청 (클라이언트 → 서버) | 알림 (서버 → 클라이언트) |\n| --- | --- | --- |\n");
        groups.forEach((tag, group) -> {
            if (group.requests.isEmpty() && group.notifications.isEmpty()) {
                return;   // 로그인, 플레이어 같은 REST 분류와 오류 분류는 건너뛴다
            }
            sb.append("| ").append(tag)
                    .append(" | ").append(join(group.requests))
                    .append(" | ").append(join(group.notifications)).append(" |\n");
        });
        return sb.toString();
    }

    private static String join(Set<String> types) {
        if (types.isEmpty()) {
            return "-";
        }
        List<String> quoted = new ArrayList<>();
        types.forEach(type -> quoted.add("`" + type + "`"));
        return String.join(", ", quoted);
    }
}