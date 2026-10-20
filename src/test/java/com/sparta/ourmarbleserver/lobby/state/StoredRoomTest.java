package com.sparta.ourmarbleserver.lobby.state;

import com.sparta.ourmarbleserver.game.state.GameConfig;
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
        room.setConfig(new GameConfig(1_000_000L, 20_000L, 50_000L, 10, 2, 3, 30));   // 기본값과 다른 설정
        room.addMember(1L, true);
        room.setNickname(1L, "철수");
        room.setPortrait(1L, 5);     // 고른 초상화. 나머지는 고르지 않음(null)
        room.addMember(3L, false);   // id 순서와 참가 순서를 일부러 다르게
        room.setNickname(3L, "영희");
        room.addMember(2L, true);
        room.setNickname(2L, "민수");

        String json = objectMapper.writeValueAsString(StoredRoom.from(room));
        Room restored = objectMapper.readValue(json, StoredRoom.class).toRoom();

        // 모든 필드를 끝까지 비교한다. Room에 필드가 생겼는데 StoredRoom에 빠뜨리면 여기서 실패한다.
        assertThat(restored).usingRecursiveComparison().isEqualTo(room);
        assertThat(restored.memberIds()).containsExactly(1L, 3L, 2L);   // 참가 순서(=턴 순서) 유지
        assertThat(restored.getMapId()).isEqualTo(3);                   // 맵 번호는 바뀐 설정에서 읽는다
        assertThat(restored.portraitOf(1L)).isEqualTo(5);               // 초상화 유지
        assertThat(restored.portraitOf(3L)).isNull();
    }

    @Test
    void 설정을_바꾸지_않은_방은_기본_설정으로_복원된다() {
        Room room = new Room("r_2", 1L, 1, 4);
        room.addMember(1L, true);
        room.setNickname(1L, "철수");

        String json = objectMapper.writeValueAsString(StoredRoom.from(room));
        Room restored = objectMapper.readValue(json, StoredRoom.class).toRoom();

        assertThat(restored.getConfig()).isEqualTo(GameConfig.defaults().withMapId(1));
        assertThat(restored.getStatus()).isEqualTo(RoomStatus.WAITING);
    }
}