package com.sparta.ourmarbleserver.lobby.service;

import com.sparta.ourmarbleserver.auth.dto.PlayerResponse;
import com.sparta.ourmarbleserver.auth.service.PlayerService;
import com.sparta.ourmarbleserver.game.event.GameEndedEvent;
import com.sparta.ourmarbleserver.game.service.GameDataService;
import com.sparta.ourmarbleserver.game.service.GameService;
import com.sparta.ourmarbleserver.game.state.GameConfig;
import com.sparta.ourmarbleserver.game.state.GameState;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.LobbyErrorCode;
import com.sparta.ourmarbleserver.lobby.dto.*;
import com.sparta.ourmarbleserver.lobby.state.InMemoryRoomRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 로비 서비스(방 생성·참가·나가기·준비·시작) 테스트. 게임 시작은 호출만 확인한다. */
class LobbyServiceTest {

    private static final long HOST = 1L;
    private static final long P2 = 2L;
    private static final long P3 = 3L;
    private static final long P4 = 4L;
    private static final int MAP = 1;

    /**
     * startGame 호출만 기록하는 GameService 대역. 실제 게임 상태는 만들지 않는다.
     * GameService를 상속하지 않고 Mockito로 만들어서, GameService의 생성자 인자가 바뀌어도 이 테스트는 안 깨진다.
     */
    private static class RecordingGameService {
        final GameService mock = mock(GameService.class);
        int callCount;
        String roomId;
        List<Long> playerIds;
        Map<Long, String> nicknames;
        Set<Long> botIds;
        boolean fail;

        RecordingGameService() {
            when(mock.startGame(anyString(), anyList(), anyMap(), anySet())).thenAnswer(invocation -> {
                if (fail) {
                    throw new IllegalStateException("시작 실패");
                }
                callCount++;
                roomId = invocation.getArgument(0);
                playerIds = new ArrayList<>(invocation.<List<Long>>getArgument(1));
                nicknames = new HashMap<>(invocation.<Map<Long, String>>getArgument(2));
                botIds = new HashSet<>(invocation.<Set<Long>>getArgument(3));
                return null;
            });
        }
    }

    /** DB 없이 닉네임을 돌려주는 PlayerService. 1번 플레이어는 "닉1"이다. */
    private static class FakePlayerService extends PlayerService {
        FakePlayerService() {
            super(null, null);
        }

        @Override
        public PlayerResponse findMe(long userId) {
            return new PlayerResponse(userId, "", "닉" + userId);
        }
    }

    private final RecordingGameService gameService = new RecordingGameService();
    private final LobbyService service =
            new LobbyService(new InMemoryRoomRepository(), gameService.mock, new FakePlayerService());

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
        assertError(LobbyErrorCode.INVALID_STATE, () -> service.createRoom(HOST, MAP, 1));
        assertError(LobbyErrorCode.INVALID_STATE, () -> service.createRoom(HOST, MAP, 5));
    }

    @Test
    void 이미_대기_중인_방에_있으면_방을_만들_수_없다() {
        service.createRoom(HOST, MAP, 4);

        assertError(LobbyErrorCode.ALREADY_IN_ROOM, () -> service.createRoom(HOST, MAP, 4));
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
        assertError(LobbyErrorCode.ROOM_NOT_FOUND, () -> service.getRoom("없는방"));
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

        assertError(LobbyErrorCode.ROOM_FULL, () -> service.join(room.roomId(), P3));
    }

    @Test
    void 같은_방에_두_번_참가할_수_없다() {
        RoomInfo room = roomWithTwo();

        assertError(LobbyErrorCode.ALREADY_IN_ROOM, () -> service.join(room.roomId(), P2));
    }

    @Test
    void 대기_중인_다른_방에_있으면_참가할_수_없다() {
        RoomInfo first = service.createRoom(HOST, MAP, 4);
        RoomInfo second = service.createRoom(P2, MAP, 4);

        assertError(LobbyErrorCode.ALREADY_IN_ROOM, () -> service.join(second.roomId(), HOST));
        assertThat(service.getRoom(first.roomId()).players()).hasSize(1);
    }

    @Test
    void 시작한_방에는_참가할_수_없다() {
        RoomInfo room = roomWithTwo();
        service.setReady(room.roomId(), P2, true);
        service.start(room.roomId(), HOST);

        assertError(LobbyErrorCode.ROOM_ALREADY_STARTED, () -> service.join(room.roomId(), P3));
    }

    @Test
    void 없는_방에_참가하면_ROOM_NOT_FOUND() {
        assertError(LobbyErrorCode.ROOM_NOT_FOUND, () -> service.join("없는방", P2));
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

        assertError(LobbyErrorCode.ROOM_NOT_FOUND, () -> service.getRoom(room.roomId()));
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

        assertError(LobbyErrorCode.NOT_IN_ROOM, () -> service.leave(room.roomId(), P2));
    }

    @Test
    void 시작한_방에서는_나갈_수_없다() {
        RoomInfo room = roomWithTwo();
        service.setReady(room.roomId(), P2, true);
        service.start(room.roomId(), HOST);

        assertError(LobbyErrorCode.ROOM_ALREADY_STARTED, () -> service.leave(room.roomId(), P2));
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

        assertError(LobbyErrorCode.INVALID_STATE, () -> service.setReady(room.roomId(), HOST, false));
    }

    @Test
    void 참가하지_않았으면_준비할_수_없다() {
        RoomInfo room = service.createRoom(HOST, MAP, 4);

        assertError(LobbyErrorCode.NOT_IN_ROOM, () -> service.setReady(room.roomId(), P2, true));
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

        assertError(LobbyErrorCode.NOT_HOST, () -> service.start(room.roomId(), P2));
        assertThat(gameService.callCount).isZero();
    }

    @Test
    void 혼자서는_시작할_수_없다() {
        RoomInfo room = service.createRoom(HOST, MAP, 4);

        assertError(LobbyErrorCode.NOT_ENOUGH_PLAYERS, () -> service.start(room.roomId(), HOST));
        assertThat(gameService.callCount).isZero();
    }

    @Test
    void 준비하지_않은_사람이_있으면_시작할_수_없다() {
        RoomInfo room = roomWithTwo();

        assertError(LobbyErrorCode.NOT_ALL_READY, () -> service.start(room.roomId(), HOST));
        assertThat(service.getRoom(room.roomId()).status()).isEqualTo(RoomStatus.WAITING);
        assertThat(gameService.callCount).isZero();
    }

    @Test
    void 이미_시작한_방은_다시_시작할_수_없다() {
        RoomInfo room = roomWithTwo();
        service.setReady(room.roomId(), P2, true);
        service.start(room.roomId(), HOST);

        assertError(LobbyErrorCode.ROOM_ALREADY_STARTED, () -> service.start(room.roomId(), HOST));
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
        assertError(LobbyErrorCode.ROOM_NOT_FOUND, () -> service.getRoom(room.roomId()));
    }

    @Test
    void 시작한_방도_삭제할_수_있다() {
        RoomInfo room = roomWithTwo();
        service.setReady(room.roomId(), P2, true);
        service.start(room.roomId(), HOST);

        service.deleteRoom(room.roomId());

        assertError(LobbyErrorCode.ROOM_NOT_FOUND, () -> service.getRoom(room.roomId()));
    }

    @Test
    void 없는_방을_삭제하면_ROOM_NOT_FOUND() {
        assertError(LobbyErrorCode.ROOM_NOT_FOUND, () -> service.deleteRoom("r_없음"));
    }

    @Test
    void 방이_삭제되면_참가자가_새_방을_만들_수_있다() {
        RoomInfo room = service.createRoom(HOST, MAP, 4);
        assertError(LobbyErrorCode.ALREADY_IN_ROOM, () -> service.createRoom(HOST, MAP, 4));

        service.deleteRoom(room.roomId());

        assertThat(service.createRoom(HOST, MAP, 4).roomId()).isNotEqualTo(room.roomId());
    }

    // ===== 게임 시작 시 닉네임 =====

    @Test
    void 게임을_시작하면_참가자_닉네임을_넘긴다() {
        RoomInfo room = roomWithTwo();
        service.setReady(room.roomId(), P2, true);

        service.start(room.roomId(), HOST);

        assertThat(gameService.nicknames).hasSize(2)
                .containsEntry(HOST, "닉1")
                .containsEntry(P2, "닉2");
        assertThat(gameService.botIds).isEmpty();
    }

    // ===== 플레이어로 방 찾기 =====

    @Test
    void 플레이어가_들어_있는_방을_찾는다() {
        RoomInfo room = roomWithTwo();

        assertThat(service.findRoomByPlayer(HOST).roomId()).isEqualTo(room.roomId());
        assertThat(service.findRoomByPlayer(P2).roomId()).isEqualTo(room.roomId());
    }

    @Test
    void 어느_방에도_없으면_NOT_IN_ROOM() {
        service.createRoom(HOST, MAP, 4);

        assertError(LobbyErrorCode.NOT_IN_ROOM, () -> service.findRoomByPlayer(P2));
    }

    @Test
    void 방에서_나가거나_방이_삭제되면_NOT_IN_ROOM() {
        RoomInfo room = roomWithTwo();

        service.leave(room.roomId(), P2);
        assertError(LobbyErrorCode.NOT_IN_ROOM, () -> service.findRoomByPlayer(P2));

        service.deleteRoom(room.roomId());
        assertError(LobbyErrorCode.NOT_IN_ROOM, () -> service.findRoomByPlayer(HOST));
    }

    @Test
    void 시작한_방에_있는_사람도_방을_찾을_수_있다() {
        RoomInfo room = roomWithTwo();
        service.setReady(room.roomId(), P2, true);
        service.start(room.roomId(), HOST);

        assertThat(service.findRoomByPlayer(P2).roomId()).isEqualTo(room.roomId());
    }

    @Test
    void 시작한_방에_있는_사람은_새_방을_만들거나_입장할_수_없다() {
        RoomInfo started = roomWithTwo();
        service.setReady(started.roomId(), P2, true);
        service.start(started.roomId(), HOST);
        RoomInfo other = service.createRoom(P3, MAP, 4);

        assertError(LobbyErrorCode.ALREADY_IN_ROOM, () -> service.createRoom(HOST, MAP, 4));
        assertError(LobbyErrorCode.ALREADY_IN_ROOM, () -> service.join(other.roomId(), P2));
    }

    // ===== 게임 종료 =====

    @Test
    void 게임이_끝나면_방이_대기_상태로_돌아가고_멤버가_유지된다() {
        RoomInfo room = startedRoom();

        service.onGameEnded(new GameEndedEvent(room.roomId()));

        RoomInfo info = service.getRoom(room.roomId());
        assertThat(info.status()).isEqualTo(RoomStatus.WAITING);
        assertThat(info.hostId()).isEqualTo(HOST);
        assertThat(info.players()).extracting(RoomPlayer::playerId).containsExactly(HOST, P2);
    }

    @Test
    void 게임이_끝나면_방장만_준비_상태가_된다() {
        RoomInfo room = startedRoom();

        service.onGameEnded(new GameEndedEvent(room.roomId()));

        assertThat(service.getRoom(room.roomId()).players())
                .containsExactly(new RoomPlayer(HOST, true), new RoomPlayer(P2, false));
    }

    @Test
    void 게임이_끝나면_방이_목록에_다시_나온다() {
        RoomInfo room = startedRoom();
        assertThat(service.listRooms()).isEmpty();

        service.onGameEnded(new GameEndedEvent(room.roomId()));

        assertThat(service.listRooms()).extracting(RoomInfo::roomId).containsExactly(room.roomId());
    }

    @Test
    void 게임이_끝난_뒤_다시_준비하고_시작할_수_있다() {
        RoomInfo room = startedRoom();
        service.onGameEnded(new GameEndedEvent(room.roomId()));

        assertError(LobbyErrorCode.NOT_ALL_READY, () -> service.start(room.roomId(), HOST));
        service.setReady(room.roomId(), P2, true);
        service.start(room.roomId(), HOST);

        assertThat(gameService.callCount).isEqualTo(2);
        assertThat(service.getRoom(room.roomId()).status()).isEqualTo(RoomStatus.PLAYING);
    }

    @Test
    void 없는_방의_게임_종료는_예외없이_넘어간다() {
        service.onGameEnded(new GameEndedEvent("r_없음"));

        assertThat(service.listRooms()).isEmpty();
    }

    /** 방장 1명과 준비를 마친 2번 플레이어로 게임을 시작한 방. */
    private RoomInfo startedRoom() {
        RoomInfo room = roomWithTwo();
        service.setReady(room.roomId(), P2, true);
        service.start(room.roomId(), HOST);
        return room;
    }

    // ===== 인원수 없이 방 만들기 (CREATE_ROOM 명세) =====

    @Test
    void 인원수와_맵을_안_주면_최대_인원과_기본_맵으로_방을_만든다() {
        RoomInfo room = service.createRoom(HOST);

        assertThat(room.maxPlayers()).isEqualTo(GameConfig.MAX_PLAYERS);
        assertThat(room.mapId()).isEqualTo(GameConfig.DEFAULT_MAP_ID);
        assertThat(room.hostId()).isEqualTo(HOST);
    }

    // ===== ROOM_UPDATED 내용 =====

    @Test
    void ROOM_UPDATED에_방_상태와_참가자_닉네임이_담긴다() {
        RoomInfo room = roomWithTwo();

        RoomUpdatedPayload.RoomSnapshot snapshot = service.roomUpdated(room.roomId()).room();

        assertThat(snapshot.roomId()).isEqualTo(room.roomId());
        assertThat(snapshot.hostId()).isEqualTo(HOST);
        assertThat(snapshot.mapId()).isEqualTo(MAP);
        assertThat(snapshot.status()).isEqualTo(RoomStatus.WAITING);
        assertThat(snapshot.players()).containsExactly(
                new RoomUpdatedPayload.PlayerSnapshot(HOST, "닉1", true),
                new RoomUpdatedPayload.PlayerSnapshot(P2, "닉2", false));
    }

    @Test
    void 나간_사람은_ROOM_UPDATED에서_빠진다() {
        RoomInfo room = roomWithTwo();

        service.leave(room.roomId(), P2);

        assertThat(service.roomUpdated(room.roomId()).room().players())
                .extracting(RoomUpdatedPayload.PlayerSnapshot::playerId).containsExactly(HOST);
    }

    @Test
    void 방장이_나가면_ROOM_UPDATED의_방장이_바뀐다() {
        RoomInfo room = roomWithTwo();

        service.leave(room.roomId(), HOST);

        RoomUpdatedPayload.RoomSnapshot snapshot = service.roomUpdated(room.roomId()).room();
        assertThat(snapshot.hostId()).isEqualTo(P2);
        assertThat(snapshot.players()).containsExactly(new RoomUpdatedPayload.PlayerSnapshot(P2, "닉2", true));
    }

    @Test
    void 없는_방의_ROOM_UPDATED는_ROOM_NOT_FOUND() {
        assertError(LobbyErrorCode.ROOM_NOT_FOUND, () -> service.roomUpdated("r_없음"));
    }

    @Test
    void 방_목록에는_대기_중인_방의_요약이_담긴다() {
        RoomInfo room = service.createRoom(HOST);
        service.join(room.roomId(), P2);

        assertThat(service.roomList().rooms()).containsExactly(
                new RoomListPayload.RoomSummary(room.roomId(), "닉1", GameConfig.DEFAULT_MAP_ID, 2));
    }

    @Test
    void 방을_나가면_플레이어로_방을_찾을_수_없다() {
        RoomInfo room = service.createRoom(HOST);
        service.join(room.roomId(), P2);

        service.leave(room.roomId(), P2);

        assertThat(service.roomIdOf(HOST)).isEqualTo(room.roomId());
        assertError(LobbyErrorCode.NOT_IN_ROOM, () -> service.roomIdOf(P2));
    }

}