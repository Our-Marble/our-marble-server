package com.sparta.ourmarbleserver.lobby.state;

import com.sparta.ourmarbleserver.lobby.dto.RoomStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class StoredRoomTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void JSON으로_저장했다가_꺼내도_방_상태가_같다() {
        Room room = new Room("r_1", 1L, 1, 4);
        room.setStatus(RoomStatus.PLAYING);
        room.addMember(1L, true);
        room.setNickname(1L, "철수");
        room.addMember(3L, false);
        room.setNickname(3L, "영희");
        room.addMember(2L, true);
        room.setNickname(2L, "민수");

        String json = objectMapper.writeValueAsString(StoredRoom.from(room));
        Room restored = objectMapper.readValue(json, StoredRoom.class).toRoom();

        assertThat(restored.getRoomId()).isEqualTo("r_1");
        assertThat(restored.getHostId()).isEqualTo(1L);
        assertThat(restored.getMaxPlayers()).isEqualTo(4);
        assertThat(restored.getStatus()).isEqualTo(RoomStatus.PLAYING);
        assertThat(restored.memberIds()).containsExactly(1L, 3L, 2L);   // 참가 순서 유지
        assertThat(restored.isReady(3L)).isFalse();
        assertThat(restored.nicknameOf(2L)).isEqualTo("민수");
    }
}