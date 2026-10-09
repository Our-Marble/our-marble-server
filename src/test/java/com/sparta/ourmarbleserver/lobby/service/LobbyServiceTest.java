package com.sparta.ourmarbleserver.lobby.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.game.service.GameDataService;
import com.sparta.ourmarbleserver.game.service.GameService;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.lobby.dto.RoomInfo;
import com.sparta.ourmarbleserver.lobby.dto.RoomPlayer;
import com.sparta.ourmarbleserver.lobby.dto.RoomStatus;
import com.sparta.ourmarbleserver.lobby.state.InMemoryRoomRepository;

import tools.jackson.databind.json.JsonMapper;

/** 로비 서비스(방 생성·참가·나가기·준비·시작) 테스트. 게임 시작은 호출만 확인한다. */
class LobbyServiceTest {

    private static final long HOST = 1L;
    private static final long P2 = 2L;
    private static final long P3 = 3L;
    private static final long P4 = 4L;
    private static final int MAP = 1;

    /** startGame 호출만 기록하는 GameService. 실제 게임 상태는 만들지 않는다. */
    private static class RecordingGameService extends GameService {
        int callCount;
        String roomId;
        List<Long> playerIds;
        boolean fail;

        RecordingGameService() {
            super( null, null, null, null, null, null,
                    new GameDataService(JsonMapper.builder().build()));
        }

        @Override
        public GameState startGame(String roomId, List<Long> playerIds) {
            if (fail) {
                throw new IllegalStateException("시작 실패");
            }
            this.callCount++;
            this.roomId = roomId;
            this.playerIds = new ArrayList<>(playerIds);
            return null;
        }
    }

    private final RecordingGameService gameService = new RecordingGameService();
    private final LobbyService service = new LobbyService(new InMemoryRoomRepository(), gameService);

    private static void assertError(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    /** 방장 1명과 2번 플레이어가 참가한 방. 2번은 아직 준비 전이다. */
    private RoomInfo roomWithTwo() {
        RoomInfo room = service.createRoom(HOST, MAP, 4);
        service.join(room.roomId(), P2);
        return service.getRoom(room.roomId());
    }

    // ===== 방 만들기 / 조회 =====

    @Test
    void 방을_만들면_만든_사람이_방장이고_준비_상태로_참가한다() {
        RoomInfo room = service.createRoom(HOST, MAP, 4);

        assertThat(room.hostId()).isEqualTo(HOST);
        assertThat(room.mapId()).isEqualTo(MAP);
        assertThat(room.maxPlayers()).isEqualTo(4);
        assertThat(room.status()).isEqualTo(RoomStatus.WAITING);
        assertThat(room.players()).containsExactly(new RoomPlayer(HOST, true));
    }

    @Test
    void 방마다_서로_다른_번호가_붙는다() {
        RoomInfo first = service.createRoom(HOST, MAP, 4);
        RoomInfo second = service.createRoom(P2, MAP, 4);

        assertThat(first.roomId()).isNotEqualTo(second.roomId());
    }

    @Test
    void 최대_인원이_2에서_4가_아니면_거부한다() {
        assertError(ErrorCode.INVALID_STATE, () -> service.createRoom(HOST, MAP, 1));
        assertError(ErrorCode.INVALID_STATE, () -> service.createRoom(HOST, MAP, 5));
    }

    @Test
    void 이미_대기_중인_방에_있으면_방을_만들_수_없다() {
        service.createRoom(HOST, MAP, 4);

        assertError(ErrorCode.ALREADY_IN_ROOM, () -> service.createRoom(HOST, MAP, 4));
    }

    @Test
    void 목록에는_대기_중인_방만_나온다() {
        RoomInfo waiting = service.createRoom(HOST, MAP, 4);
        RoomInfo started = service.createRoom(P3, MAP, 4);
        service.join(started.roomId(), P4);
        service.setReady(started.roomId(), P4, true);
        service.start(started.roomId(), P3);

        assertThat(service.listRooms()).extracting(RoomInfo::roomId).containsExactly(waiting.roomId());
    }

    @Test
    void 없는_방을_조회하면_ROOM_NOT_FOUND() {
        assertError(ErrorCode.ROOM_NOT_FOUND, () -> service.getRoom("없는방"));
    }

    // ===== 참가 =====

    @Test
    void 참가하면_준비_전_상태로_참가_순서대로_들어간다() {
        RoomInfo room = service.createRoom(HOST, MAP, 4);

        service.join(room.roomId(), P2);
        RoomInfo joined = service.join(room.roomId(), P3);

        assertThat(joined.players()).containsExactly(
                new RoomPlayer(HOST, true), new RoomPlayer(P2, false), new RoomPlayer(P3, false));
    }

    @Test
    void 가득_찬_방에는_참가할_수_없다() {
        RoomInfo room = service.createRoom(HOST, MAP, 2);
        service.join(room.roomId(), P2);

        assertError(ErrorCode.ROOM_FULL, () -> service.join(room.roomId(), P3));
    }

    @Test
    void 같은_방에_두_번_참가할_수_없다() {
        RoomInfo room = roomWithTwo();

        assertError(ErrorCode.ALREADY_IN_ROOM, () -> service.join(room.roomId(), P2));
    }

    @Test
    void 대기_중인_다른_방에_있으면_참가할_수_없다() {
        RoomInfo first = service.createRoom(HOST, MAP, 4);
        RoomInfo second = service.createRoom(P2, MAP, 4);

        assertError(ErrorCode.ALREADY_IN_ROOM, () -> service.join(second.roomId(), HOST));
        assertThat(service.getRoom(first.roomId()).players()).hasSize(1);
    }

    @Test
    void 시작한_방에는_참가할_수_없다() {
        RoomInfo room = roomWithTwo();
        service.setReady(room.roomId(), P2, true);
        service.start(room.roomId(), HOST);

        assertError(ErrorCode.ROOM_ALREADY_STARTED, () -> service.join(room.roomId(), P3));
    }

    @Test
    void 없는_방에_참가하면_ROOM_NOT_FOUND() {
        assertError(ErrorCode.ROOM_NOT_FOUND, () -> service.join("없는방", P2));
    }

    // ===== 나가기 =====

    @Test
    void 방장이_아닌_참가자가_나가면_목록에서_빠진다() {
        RoomInfo room = roomWithTwo();

        service.leave(room.roomId(), P2);

        assertThat(service.getRoom(room.roomId()).players()).containsExactly(new RoomPlayer(HOST, true));
        assertThat(service.getRoom(room.roomId()).hostId()).isEqualTo(HOST);
    }

    @Test
    void 방장이_나가면_가장_먼저_참가한_사람이_방장이_되고_준비_상태가_된다() {
        RoomInfo room = roomWithTwo();
        service.join(room.roomId(), P3);

        service.leave(room.roomId(), HOST);

        RoomInfo after = service.getRoom(room.roomId());
        assertThat(after.hostId()).isEqualTo(P2);
        assertThat(after.players()).containsExactly(new RoomPlayer(P2, true), new RoomPlayer(P3, false));
    }

    @Test
    void 마지막_사람이_나가면_방이_삭제된다() {
        RoomInfo room = service.createRoom(HOST, MAP, 4);

        service.leave(room.roomId(), HOST);

        assertError(ErrorCode.ROOM_NOT_FOUND, () -> service.getRoom(room.roomId()));
        assertThat(service.listRooms()).isEmpty();
    }

    @Test
    void 나간_사람은_다시_방을_만들_수_있다() {
        RoomInfo room = service.createRoom(HOST, MAP, 4);
        service.leave(room.roomId(), HOST);

        RoomInfo again = service.createRoom(HOST, MAP, 4);

        assertThat(again.hostId()).isEqualTo(HOST);
    }

    @Test
    void 참가하지_않은_방에서_나가면_NOT_IN_ROOM() {
        RoomInfo room = service.createRoom(HOST, MAP, 4);

        assertError(ErrorCode.NOT_IN_ROOM, () -> service.leave(room.roomId(), P2));
    }

    @Test
    void 시작한_방에서는_나갈_수_없다() {
        RoomInfo room = roomWithTwo();
        service.setReady(room.roomId(), P2, true);
        service.start(room.roomId(), HOST);

        assertError(ErrorCode.ROOM_ALREADY_STARTED, () -> service.leave(room.roomId(), P2));
    }

    // ===== 준비 =====

    @Test
    void 준비를_켜고_끌_수_있다() {
        RoomInfo room = roomWithTwo();

        RoomInfo ready = service.setReady(room.roomId(), P2, true);
        assertThat(ready.players()).contains(new RoomPlayer(P2, true));

        RoomInfo notReady = service.setReady(room.roomId(), P2, false);
        assertThat(notReady.players()).contains(new RoomPlayer(P2, false));
    }

    @Test
    void 방장은_준비_상태를_바꿀_수_없다() {
        RoomInfo room = roomWithTwo();

        assertError(ErrorCode.INVALID_STATE, () -> service.setReady(room.roomId(), HOST, false));
    }

    @Test
    void 참가하지_않았으면_준비할_수_없다() {
        RoomInfo room = service.createRoom(HOST, MAP, 4);

        assertError(ErrorCode.NOT_IN_ROOM, () -> service.setReady(room.roomId(), P2, true));
    }

    // ===== 시작 =====

    @Test
    void 전원이_준비하면_방장이_시작할_수_있고_참가_순서대로_게임이_시작된다() {
        RoomInfo room = roomWithTwo();
        service.join(room.roomId(), P3);
        service.setReady(room.roomId(), P2, true);
        service.setReady(room.roomId(), P3, true);

        RoomInfo started = service.start(room.roomId(), HOST);

        assertThat(started.status()).isEqualTo(RoomStatus.PLAYING);
        assertThat(gameService.callCount).isEqualTo(1);
        assertThat(gameService.roomId).isEqualTo(room.roomId());
        assertThat(gameService.playerIds).containsExactly(HOST, P2, P3);
    }

    @Test
    void 방장이_아니면_시작할_수_없다() {
        RoomInfo room = roomWithTwo();
        service.setReady(room.roomId(), P2, true);

        assertError(ErrorCode.NOT_HOST, () -> service.start(room.roomId(), P2));
        assertThat(gameService.callCount).isZero();
    }

    @Test
    void 혼자서는_시작할_수_없다() {
        RoomInfo room = service.createRoom(HOST, MAP, 4);

        assertError(ErrorCode.NOT_ENOUGH_PLAYERS, () -> service.start(room.roomId(), HOST));
        assertThat(gameService.callCount).isZero();
    }

    @Test
    void 준비하지_않은_사람이_있으면_시작할_수_없다() {
        RoomInfo room = roomWithTwo();

        assertError(ErrorCode.NOT_ALL_READY, () -> service.start(room.roomId(), HOST));
        assertThat(service.getRoom(room.roomId()).status()).isEqualTo(RoomStatus.WAITING);
        assertThat(gameService.callCount).isZero();
    }

    @Test
    void 이미_시작한_방은_다시_시작할_수_없다() {
        RoomInfo room = roomWithTwo();
        service.setReady(room.roomId(), P2, true);
        service.start(room.roomId(), HOST);

        assertError(ErrorCode.ROOM_ALREADY_STARTED, () -> service.start(room.roomId(), HOST));
        assertThat(gameService.callCount).isEqualTo(1);
    }

    @Test
    void 게임_시작이_실패하면_방은_대기_상태로_남는다() {
        RoomInfo room = roomWithTwo();
        service.setReady(room.roomId(), P2, true);
        gameService.fail = true;

        assertThatThrownBy(() -> service.start(room.roomId(), HOST)).isInstanceOf(IllegalStateException.class);

        assertThat(service.getRoom(room.roomId()).status()).isEqualTo(RoomStatus.WAITING);
    }


    // ===== 방 삭제 =====

    @Test
    void 방을_삭제하면_목록과_조회에서_사라진다() {
        RoomInfo room = roomWithTwo();

        service.deleteRoom(room.roomId());

        assertThat(service.listRooms()).isEmpty();
        assertError(ErrorCode.ROOM_NOT_FOUND, () -> service.getRoom(room.roomId()));
    }

    @Test
    void 시작한_방도_삭제할_수_있다() {
        RoomInfo room = roomWithTwo();
        service.setReady(room.roomId(), P2, true);
        service.start(room.roomId(), HOST);

        service.deleteRoom(room.roomId());

        assertError(ErrorCode.ROOM_NOT_FOUND, () -> service.getRoom(room.roomId()));
    }

    @Test
    void 없는_방을_삭제하면_ROOM_NOT_FOUND() {
        assertError(ErrorCode.ROOM_NOT_FOUND, () -> service.deleteRoom("r_없음"));
    }

    @Test
    void 방이_삭제되면_참가자가_새_방을_만들_수_있다() {
        RoomInfo room = service.createRoom(HOST, MAP, 4);
        assertError(ErrorCode.ALREADY_IN_ROOM, () -> service.createRoom(HOST, MAP, 4));

        service.deleteRoom(room.roomId());

        assertThat(service.createRoom(HOST, MAP, 4).roomId()).isNotEqualTo(room.roomId());
    }
}