package com.sparta.ourmarbleserver.lobby.state;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRoomRepositoryTest {

    private final InMemoryRoomRepository repository = new InMemoryRoomRepository();

    private Room roomWith(String roomId, long... playerIds) {
        Room room = new Room(roomId, playerIds[0], 1, 4);
        for (long playerId : playerIds) {
            room.addMember(playerId, false);
        }
        return room;
    }

    @Test
    void 어느_방에도_없으면_empty() {
        assertThat(repository.findRoomIdByPlayerId(1L)).isEmpty();
    }

    @Test
    void 방을_저장하면_멤버마다_색인된다() {
        repository.save(roomWith("r_1", 1L, 2L));

        assertThat(repository.findRoomIdByPlayerId(1L)).contains("r_1");
        assertThat(repository.findRoomIdByPlayerId(2L)).contains("r_1");
    }

    @Test
    void 새로_들어온_사람은_다음_save에서_색인된다() {
        Room room = roomWith("r_1", 1L);
        repository.save(room);

        room.addMember(2L, false);
        repository.save(room);

        assertThat(repository.findRoomIdByPlayerId(2L)).contains("r_1");
    }

    @Test
    void 나간_사람은_다음_save에서_색인에서_빠진다() {
        Room room = roomWith("r_1", 1L, 2L);
        repository.save(room);

        room.removeMember(2L);
        repository.save(room);

        assertThat(repository.findRoomIdByPlayerId(2L)).isEmpty();
        assertThat(repository.findRoomIdByPlayerId(1L)).contains("r_1");
    }

    @Test
    void 방을_지우면_그_방의_색인이_모두_빠진다() {
        repository.save(roomWith("r_1", 1L, 2L));

        repository.deleteById("r_1");

        assertThat(repository.findRoomIdByPlayerId(1L)).isEmpty();
        assertThat(repository.findRoomIdByPlayerId(2L)).isEmpty();
    }

    @Test
    void 방을_옮기면_새_방만_남는다() {
        Room first = roomWith("r_1", 1L, 2L);
        repository.save(first);
        first.removeMember(2L);
        repository.save(first);

        repository.save(roomWith("r_2", 2L));

        assertThat(repository.findRoomIdByPlayerId(2L)).contains("r_2");
    }

    @Test
    void 옛_방이_늦게_저장돼도_새_방_기록은_지워지지_않는다() {
        Room first = roomWith("r_1", 1L, 2L);
        repository.save(first);
        repository.save(roomWith("r_2", 2L));   // 2번이 r_2로 옮김

        first.removeMember(2L);
        repository.save(first);                  // r_1이 뒤늦게 저장됨

        assertThat(repository.findRoomIdByPlayerId(2L)).contains("r_2");
    }

    @Test
    void 없는_방을_지워도_문제없다() {
        repository.deleteById("없는방");

        assertThat(repository.findAll()).isEmpty();
    }
}