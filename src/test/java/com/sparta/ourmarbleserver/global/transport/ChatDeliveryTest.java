package com.sparta.ourmarbleserver.global.transport;

import com.sparta.ourmarbleserver.auth.dto.PlayerResponse;
import com.sparta.ourmarbleserver.auth.service.PlayerService;
import com.sparta.ourmarbleserver.bot.service.BotService;
import com.sparta.ourmarbleserver.game.service.GameService;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.LobbyErrorCode;
import com.sparta.ourmarbleserver.global.websocket.SessionRegistry;
import com.sparta.ourmarbleserver.global.websocket.WebSocketMessageSender;
import com.sparta.ourmarbleserver.lobby.dto.RoomStatus;
import com.sparta.ourmarbleserver.lobby.service.ChatService;
import com.sparta.ourmarbleserver.lobby.service.LobbyService;
import com.sparta.ourmarbleserver.lobby.state.InMemoryRoomRepository;
import com.sparta.ourmarbleserver.lobby.state.Room;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 로비 채팅이 방 멤버의 연결까지 실제로 가는지 확인한다.
 * 클라이언트 요청 해석(MessageCodec) → 라우터 → ChatMessageHandler → ChatService → 전송 → 방 멤버 전달(RoomBroadcaster)을
 * 모두 실제 객체로 잇고, Redis 발행만 바로 구독 처리(MessageRelay.onMessage)로 넘기게 바꿔 끼운다.
 * 연결(SessionRegistry)에 실제로 나간 JSON을 확인한다.
 */
class ChatDeliveryTest {

    private static final long A1 = 1L;
    private static final long A2 = 2L;
    private static final long B1 = 3L;
    private static final long LOBBY = 9L;   // 방에 들어가지 않은 사람

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageCodec codec = new MessageCodec(objectMapper);
    private final InMemoryRoomRepository repository = new InMemoryRoomRepository();
    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);
    private final LobbyService lobbyService =
            new LobbyService(repository, mock(GameService.class), new FakePlayerService(), mock(BotService.class));
    private final MessageRouter router = new MessageRouter(java.util.List.of(
            new ChatMessageHandler(new ChatService(lobbyService), newSender(), objectMapper)));

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

    /** Redis 발행을 같은 JVM의 구독 처리로 바로 넘기는 sender. */
    private WebSocketMessageSender newSender() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        MessageRelay relay = new MessageRelay(redisTemplate, new RoomBroadcaster(repository, sessionRegistry));
        doAnswer(invocation -> {
            String channel = invocation.getArgument(0);
            String body = invocation.getArgument(1);
            relay.onMessage(new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8)), null);
            return null;
        }).when(redisTemplate).convertAndSend(anyString(), anyString());
        return new WebSocketMessageSender(codec, mock(PlayerUnicaster.class), relay);
    }

    /** 클라이언트가 보낸 것처럼 JSON 문자열을 해석해 라우터로 넘긴다. */
    private void clientSends(long playerId, String json) {
        router.route(playerId, codec.decode(json));
    }

    private String roomA() {
        String roomId = lobbyService.createRoom(A1).roomId();
        lobbyService.join(roomId, A2);
        return roomId;
    }

    /** playerId의 연결에 나간 JSON 중 마지막 것. */
    private JsonNode lastSentTo(long playerId) {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(sessionRegistry, atLeastOnce()).send(eq(playerId), json.capture());
        return objectMapper.readTree(json.getAllValues().getLast());
    }

    @Test
    void 같은_방_두_명이_서로의_채팅을_받고_보낸_본인도_받는다() {
        roomA();

        clientSends(A1, "{\"type\":\"CHAT_SEND\",\"content\":\"하이\"}");

        for (long receiver : new long[]{A1, A2}) {
            JsonNode message = lastSentTo(receiver);
            assertThat(message.get("type").asString()).isEqualTo("CHAT_BROADCAST");
            assertThat(message.get("playerId").asLong()).isEqualTo(A1);
            assertThat(message.get("content").asString()).isEqualTo("하이");
        }

        clientSends(A2, "{\"type\":\"CHAT_SEND\",\"content\":\"반가워\"}");

        for (long receiver : new long[]{A1, A2}) {
            JsonNode message = lastSentTo(receiver);
            assertThat(message.get("playerId").asLong()).isEqualTo(A2);
            assertThat(message.get("content").asString()).isEqualTo("반가워");
        }
    }

    @Test
    void 다른_방과_로비에_있는_사람에게는_전달되지_않는다() {
        roomA();
        lobbyService.createRoom(B1);   // 방 B

        clientSends(A1, "{\"type\":\"CHAT_SEND\",\"content\":\"하이\"}");

        verify(sessionRegistry).send(eq(A1), anyString());
        verify(sessionRegistry).send(eq(A2), anyString());
        verify(sessionRegistry, never()).send(eq(B1), anyString());
        verify(sessionRegistry, never()).send(eq(LOBBY), anyString());
    }

    @Test
    void 방에_없는_사람이_보내면_NOT_IN_ROOM이고_아무에게도_가지_않는다() {
        roomA();

        assertThatThrownBy(() -> clientSends(LOBBY, "{\"type\":\"CHAT_SEND\",\"content\":\"하이\"}"))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(LobbyErrorCode.NOT_IN_ROOM));

        verifyNoInteractions(sessionRegistry);
    }

    @Test
    void 게임이_시작된_방에서는_INVALID_STATE이고_아무에게도_가지_않는다() {
        String roomId = roomA();
        Room room = repository.findById(roomId).orElseThrow();
        room.setStatus(RoomStatus.PLAYING);
        repository.save(room);

        assertThatThrownBy(() -> clientSends(A1, "{\"type\":\"CHAT_SEND\",\"content\":\"하이\"}"))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(LobbyErrorCode.INVALID_STATE));

        verifyNoInteractions(sessionRegistry);
    }

    @Test
    void 내용이_비어_있거나_공백뿐이면_아무에게도_가지_않는다() {
        roomA();

        clientSends(A1, "{\"type\":\"CHAT_SEND\",\"content\":\"\"}");
        clientSends(A1, "{\"type\":\"CHAT_SEND\",\"content\":\"   \"}");

        verify(sessionRegistry, never()).send(anyLong(), anyString());
    }

    @Test
    void 글자_수는_서버에서_제한하지_않는다() {
        roomA();
        String longText = "가".repeat(500);

        clientSends(A1, "{\"type\":\"CHAT_SEND\",\"content\":\"" + longText + "\"}");

        assertThat(lastSentTo(A2).get("content").asString()).isEqualTo(longText);
    }
}
