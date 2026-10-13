package com.sparta.ourmarbleserver.global.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오류 코드가 속한 enum에 따라 클라에 가는 메시지 type이 정해지는지 확인한다.
 * 클라는 type으로 로비/게임 화면 중 어디서 처리할지 고른다.
 */
class ErrorCodeTypeTest {

    @Test
    void 공통_오류는_COMMON_ERROR로_보낸다() {
        for (CommonErrorCode code : CommonErrorCode.values()) {
            assertThat(code.type()).isEqualTo(MessageType.COMMON_ERROR);
        }
    }

    @Test
    void 로비_오류는_LOBBY_ERROR로_보낸다() {
        for (LobbyErrorCode code : LobbyErrorCode.values()) {
            assertThat(code.type()).isEqualTo(MessageType.LOBBY_ERROR);
        }
    }

    @Test
    void 게임_오류는_GAME_ERROR로_보낸다() {
        for (GameErrorCode code : GameErrorCode.values()) {
            assertThat(code.type()).isEqualTo(MessageType.GAME_ERROR);
        }
    }

    @Test
    void 같은_이름이어도_enum이_다르면_type이_다르다() {
        assertThat(LobbyErrorCode.INVALID_STATE.type()).isEqualTo(MessageType.LOBBY_ERROR);
        assertThat(GameErrorCode.INVALID_STATE.type()).isEqualTo(MessageType.GAME_ERROR);
        assertThat(ErrorPayload.of(LobbyErrorCode.INVALID_STATE).code()).isEqualTo("INVALID_STATE");
    }
}
