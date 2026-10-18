package com.sparta.ourmarbleserver.lobby.service;

import com.sparta.ourmarbleserver.auth.dto.PlayerResponse;
import com.sparta.ourmarbleserver.auth.service.PlayerService;
import com.sparta.ourmarbleserver.bot.service.BotService;
import com.sparta.ourmarbleserver.game.service.GameService;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.global.protocol.LobbyErrorCode;
import com.sparta.ourmarbleserver.lobby.dto.RoomInfo;
import com.sparta.ourmarbleserver.lobby.dto.RoomStatus;
import com.sparta.ourmarbleserver.lobby.state.InMemoryRoomLock;
import com.sparta.ourmarbleserver.lobby.state.InMemoryRoomRepository;
import com.sparta.ourmarbleserver.lobby.state.Room;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** 로비 채팅 도메인 서비스 테스트. 채팅을 보낼 방을 제대로 알려 주는지, 보낼 수 없는 상태를 거절하는지 확인한다. */
class ChatServiceTest {

    private static final long A1 = 1L;
    private static final long A2 = 2L;
    private static final long B1 = 3L;
    private static final long LOBBY = 9L;   // 방에 들어가지 않은 사람

    private final InMemoryRoomRepository repository = new InMemoryRoomRepository();
    private final LobbyService lobbyService =
            new LobbyService(repository, mock(GameService.class), new FakePlayerService(), mock(BotService.class), new InMemoryRoomLock());
    private final ChatService chatService = new ChatService(lobbyService);

    /** DB 없이 닉네임을 돌려주는 PlayerService. */
    private static class FakePlayerService extends PlayerService {
        FakePlayerService() {
            super(null, null);
        }

        @Override
        public PlayerResponse findMe(long userId) {
            return new PlayerResponse(userId, "", "닉" + userId);
        }
    }

    private static void assertError(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    /** 1번이 방장인 방에 2번이 들어온 방 A. */
    private String roomA() {
        RoomInfo room = lobbyService.createRoom(A1);
        lobbyService.join(room.roomId(), A2);
        return room.roomId();
    }

    @Test
    void 같은_방_사람이_보내면_그_방_id를_알려_준다() {
        String roomId = roomA();

        assertThat(chatService.roomIdToSend(A1, "하이")).contains(roomId);
        assertThat(chatService.roomIdToSend(A2, "하이")).contains(roomId);
    }

    @Test
    void 방마다_자기_방_id를_알려_준다() {
        String roomA = roomA();
        String roomB = lobbyService.createRoom(B1).roomId();

        assertThat(chatService.roomIdToSend(A1, "하이")).contains(roomA);
        assertThat(chatService.roomIdToSend(B1, "하이")).contains(roomB);
    }

    @Test
    void 방에_없는_사람이_보내면_NOT_IN_ROOM() {
        roomA();

        assertError(LobbyErrorCode.NOT_IN_ROOM, () -> chatService.roomIdToSend(LOBBY, "하이"));
    }

    @Test
    void 게임이_시작된_방에서는_INVALID_STATE() {
        String roomId = roomA();
        Room room = repository.findById(roomId).orElseThrow();
        room.setStatus(RoomStatus.PLAYING);
        repository.save(room);

        assertError(LobbyErrorCode.INVALID_STATE, () -> chatService.roomIdToSend(A1, "하이"));
    }

    @Test
    void 내용이_null이거나_비어_있거나_공백뿐이면_보낼_방이_없다() {
        roomA();

        assertThat(chatService.roomIdToSend(A1, null)).isEmpty();
        assertThat(chatService.roomIdToSend(A1, "")).isEmpty();
        assertThat(chatService.roomIdToSend(A1, "   ")).isEmpty();
        assertThat(chatService.roomIdToSend(LOBBY, "")).isEmpty();   // 방에 없어도 오류 없이 무시
    }

    @Test
    void 글자_수는_제한하지_않는다() {
        String roomId = roomA();

        assertThat(chatService.roomIdToSend(A1, "가".repeat(500))).contains(roomId);
    }
}
