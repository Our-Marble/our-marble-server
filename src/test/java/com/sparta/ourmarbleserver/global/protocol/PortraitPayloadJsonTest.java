package com.sparta.ourmarbleserver.global.protocol;

import com.sparta.ourmarbleserver.lobby.dto.RoomUpdatedPayload.PlayerSnapshot;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** 초상화(portraitId)가 알림 JSON에 어떻게 실리는지 확인한다. 고르지 않았거나 봇이면 필드가 빠진다. */
class PortraitPayloadJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void CONNECTED에는_저장된_초상화가_담긴다() {
        JsonNode json = mapper.valueToTree(new ConnectedPayload(7L, "Alice", 3));

        assertThat(json.get("playerId").asLong()).isEqualTo(7L);
        assertThat(json.get("nickname").asString()).isEqualTo("Alice");
        assertThat(json.get("portraitId").asInt()).isEqualTo(3);
    }

    @Test
    void CONNECTED에서_고른_적이_없으면_portraitId가_빠진다() {
        JsonNode json = mapper.valueToTree(new ConnectedPayload(7L, "Alice"));

        assertThat(json.has("portraitId")).isFalse();
        assertThat(json.get("nickname").asString()).isEqualTo("Alice");
    }

    @Test
    void ROOM_UPDATED의_사람은_초상화가_담기고_bot은_그대로_유지된다() {
        JsonNode json = mapper.valueToTree(new PlayerSnapshot(1L, "Alice", true, false, 5));

        assertThat(json.get("portraitId").asInt()).isEqualTo(5);
        assertThat(json.get("bot").asBoolean()).isFalse();
    }

    @Test
    void ROOM_UPDATED의_봇은_portraitId가_빠지고_bot은_true다() {
        JsonNode json = mapper.valueToTree(new PlayerSnapshot(-1L, "봇1", true, true, null));

        assertThat(json.has("portraitId")).isFalse();
        assertThat(json.get("bot").asBoolean()).isTrue();
    }
}
