package com.sparta.ourmarbleserver.config;

import java.util.EnumMap;
import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.sparta.ourmarbleserver.global.protocol.CommonErrorCode;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.GameErrorCode;
import com.sparta.ourmarbleserver.global.protocol.LobbyErrorCode;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

/**
 * 오류 알림 문서(/docs/notifications/common-error, lobby-error, game-error)에 오류 코드 표를 붙인다.
 * code와 message는 각 오류 코드 enum에서 읽어서, enum에 코드를 추가하면 표에 자동으로 나온다.
 * 상황은 enum에 없는 값이라 아래 COMMON, LOBBY, GAME에 직접 적는다. (적지 않으면 "(설명 없음)"으로 나온다)
 */
@Configuration
public class ErrorCodeDocConfig {

    private static final Map<CommonErrorCode, String> COMMON = new EnumMap<>(CommonErrorCode.class);
    private static final Map<LobbyErrorCode, String> LOBBY = new EnumMap<>(LobbyErrorCode.class);
    private static final Map<GameErrorCode, String> GAME = new EnumMap<>(GameErrorCode.class);

    static {
        COMMON.put(CommonErrorCode.INVALID_MESSAGE, "JSON이 아니거나, type이 없거나 모르는 값이거나, 이 type을 처리하는 곳이 없음. 본문의 필드 형식이 틀리거나 방입장의 roomId가 비어 있음");

        LOBBY.put(LobbyErrorCode.INVALID_STATE, "방장이 준비·준비 취소를 보냄 (방장은 항상 준비 상태), 방 설정 변경 요청에 설정이 없음");
        LOBBY.put(LobbyErrorCode.ROOM_NOT_FOUND, "없는 방에 입장");
        LOBBY.put(LobbyErrorCode.ROOM_FULL, "가득 찬 방에 입장");
        LOBBY.put(LobbyErrorCode.ALREADY_IN_ROOM, "이미 방에 있는데 방만들기·방입장");
        LOBBY.put(LobbyErrorCode.NOT_IN_ROOM, "방에 없는데 방나가기·준비·게임시작");
        LOBBY.put(LobbyErrorCode.NOT_HOST, "방장이 아닌데 게임시작·방 설정 변경");
        LOBBY.put(LobbyErrorCode.ROOM_ALREADY_STARTED, "시작한 방에 입장·나가기·준비·게임시작·방 설정 변경");
        LOBBY.put(LobbyErrorCode.NOT_ENOUGH_PLAYERS, "2명 미만으로 게임시작");
        LOBBY.put(LobbyErrorCode.NOT_ALL_READY, "준비 안 한 사람이 있는데 게임시작");

        GAME.put(GameErrorCode.INVALID_STATE, "진행 중인 게임이 없거나, 현재 단계(phase)에서 받을 수 없는 요청");
        GAME.put(GameErrorCode.NOT_YOUR_TURN, "내 차례가 아닌데 요청");
        GAME.put(GameErrorCode.NOT_ENOUGH_MONEY, "구매·건설·인수에 필요한 현금이 모자람");
        GAME.put(GameErrorCode.INVALID_PROPERTY, "없는 땅, 내 말이 서 있지 않은 땅, 올바르지 않은 목적지 칸");
        GAME.put(GameErrorCode.ALREADY_OWNED, "주인 있는 땅 구매");
        GAME.put(GameErrorCode.NOT_OWNER, "내 땅이 아닌데 건설·매각");
        GAME.put(GameErrorCode.CANNOT_BUILD, "건설 불가 칸 (독도 등)");
        GAME.put(GameErrorCode.MAX_LEVEL, "이미 호텔");
        GAME.put(GameErrorCode.CANNOT_ACQUIRE, "주인 없는 땅이나 내 땅 인수");
        GAME.put(GameErrorCode.NOT_ENOUGH_SELL, "고른 땅을 팔아도 통행료 부족");
        GAME.put(GameErrorCode.PLAYER_BANKRUPT, "파산한 플레이어의 요청");
        GAME.put(GameErrorCode.INVALID_PROPERTY_LIST, "매각 목록이 비었거나 중복");
    }

    @Bean
    public OpenApiCustomizer errorCodeTableCustomizer() {
        return openApi -> {
            appendTable(openApi, "/docs/notifications/common-error", CommonErrorCode.class, COMMON);
            appendTable(openApi, "/docs/notifications/lobby-error", LobbyErrorCode.class, LOBBY);
            appendTable(openApi, "/docs/notifications/game-error", GameErrorCode.class, GAME);
        };
    }

    /** 문서 설명 끝에 오류 코드 표를 붙인다. 문서 주소가 없으면 아무것도 하지 않는다. */
    private static <E extends Enum<E> & ErrorCode> void appendTable(
            OpenAPI openApi, String path, Class<E> type, Map<E, String> situations) {
        PathItem item = openApi.getPaths() == null ? null : openApi.getPaths().get(path);
        if (item == null || item.getGet() == null) {
            return;
        }
        Operation operation = item.getGet();
        String base = operation.getDescription() == null ? "" : operation.getDescription();
        operation.setDescription(base + "\n\n" + table(type, situations));
    }

    /** enum 선언 순서대로 마크다운 표를 만든다. */
    private static <E extends Enum<E> & ErrorCode> String table(Class<E> type, Map<E, String> situations) {
        StringBuilder sb = new StringBuilder("**오류 코드** (`").append(type.getSimpleName()).append("`)\n\n");
        sb.append("| code | message | 상황 |\n| --- | --- | --- |\n");
        for (E code : type.getEnumConstants()) {
            sb.append("| `").append(code.name()).append("`")
                    .append(" | ").append(code.message())
                    .append(" | ").append(situations.getOrDefault(code, "(설명 없음)")).append(" |\n");
        }
        return sb.toString();
    }
}