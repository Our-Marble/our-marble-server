package com.sparta.ourmarbleserver.lobby.service;

import com.sparta.ourmarbleserver.auth.service.PlayerService;
import com.sparta.ourmarbleserver.bot.service.BotService;
import com.sparta.ourmarbleserver.game.event.GameEndedEvent;
import com.sparta.ourmarbleserver.game.service.GameService;
import com.sparta.ourmarbleserver.game.state.GameConfig;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.LobbyErrorCode;
import com.sparta.ourmarbleserver.lobby.dto.*;
import com.sparta.ourmarbleserver.lobby.state.Room;
import com.sparta.ourmarbleserver.lobby.state.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 로비 도메인 서비스. 웹소켓 요청을 받는 쪽(LobbyMessageHandler)이 이 클래스의 메서드만 호출한다.
 * playerId는 접속할 때 확인된 값이 전송 계층에서 넘어온다.
 * 규칙에 어긋난 요청은 GameException(LobbyErrorCode.*)을 던지고, 전송 계층이 LOBBY_ERROR로 바꿔 보낸다.
 * 방 하나를 여러 요청이 동시에 바꿀 수 있어서 모든 메서드를 synchronized로 직렬 처리한다.
 * 게임이 끝나면 게임 쪽이 GameEndedEvent를 내보내고, onGameEnded가 받아 방을 대기(WAITING) 상태로 되돌린다.
 * 멤버는 그대로 두고 방장이 아닌 사람의 준비는 푼다.
 * (GameService가 LobbyService를 직접 부르면 서로를 참조해서 서버가 뜨지 않으므로 이벤트로 받는다)
 */
@Service
@RequiredArgsConstructor
public class LobbyService {

    private final RoomRepository repository;
    private final GameService gameService;
    private final PlayerService playerService;

    // ==== 방 만들기 / 조회 ====

    /**
     * 방을 만든다. 인원수는 게임 최대 인원(GameConfig.MAX_PLAYERS), 맵은 기본 맵(GameConfig.DEFAULT_MAP_ID)으로 정한다.
     * (CREATE_ROOM 요청에는 본문이 없다)
     */
    public synchronized RoomInfo createRoom(long playerId) {
        return createRoom(playerId, GameConfig.DEFAULT_MAP_ID, GameConfig.MAX_PLAYERS);
    }

    /**
     * 방을 만든다. 만든 사람이 방장이고 자동으로 참가한다.
     * maxPlayers가 2~4가 아니면 INVALID_STATE, 이미 어떤 방에든(시작한 방 포함) 있으면 ALREADY_IN_ROOM.
     * mapId는 검증하지 않는다. (맵 목록이 서버에 없다)
     */
    public synchronized RoomInfo createRoom(long playerId, int mapId, int maxPlayers) {
        if (maxPlayers < GameConfig.MIN_PLAYERS || maxPlayers > GameConfig.MAX_PLAYERS) {
            throw new GameException(LobbyErrorCode.INVALID_STATE);
        }
        requireNotInAnyRoom(playerId);

        Room room = new Room(repository.nextRoomId(), playerId, mapId, maxPlayers);
        room.addMember(playerId, true);
        room.setNickname(playerId, nicknameOf(playerId));
        repository.save(room);
        return toInfo(room);
    }

    /** 대기 중(WAITING)인 방 목록 */
    public synchronized List<RoomInfo> listRooms() {
        return repository.findAll().stream()
                .filter(room -> room.getStatus() == RoomStatus.WAITING)
                .map(this::toInfo)
                .toList();
    }

    /** ROOM_LIST 알림 내용. 대기 중(WAITING)인 방만 담는다. */
    public synchronized RoomListPayload roomList() {
        List<RoomListPayload.RoomSummary> rooms = repository.findAll().stream()
                .filter(room -> room.getStatus() == RoomStatus.WAITING)
                .map(room -> new RoomListPayload.RoomSummary(
                        room.getRoomId(), room.nicknameOf(room.getHostId()), room.getMapId(), room.size(), room.getMaxPlayers()))
                .toList();
        return new RoomListPayload(rooms);
    }

    /** 방 한 개 조회. 없으면 ROOM_NOT_FOUND. */
    public synchronized RoomInfo getRoom(String roomId) {
        return toInfo(findRoom(roomId));
    }

    /**
     * 이 플레이어가 들어 있는 방 id. 어느 방에도 없으면 NOT_IN_ROOM.
     * 요청에는 roomId가 실리지 않아서(LEAVE_ROOM, SET_READY, START_GAME) 요청한 사람의 방을 이걸로 찾는다.
     * 한 플레이어는 방 하나에만 들어갈 수 있다. (시작한 방 포함)
     */
    public synchronized String roomIdOf(long playerId) {
        return repository.findRoomIdByPlayerId(playerId)
                .orElseThrow(() -> new GameException(LobbyErrorCode.NOT_IN_ROOM));
    }

    /** 게임 중(PLAYING)인 방에 있으면 그 방 id. 재접속 시 게임 화면으로 돌려보낼 때 쓴다. */
    public synchronized Optional<String> playingRoomIdOf(long playerId) {
        return repository.findRoomIdByPlayerId(playerId)
                .flatMap(repository::findById)
                .filter(room -> room.getStatus() == RoomStatus.PLAYING)
                .map(Room::getRoomId);
    }

    /** 방에 사람이 한 명이라도 있는지. (봇만 남았는지 가릴 때 쓴다) */
    private boolean hasHuman(Room room) {
        return room.memberIds().stream().anyMatch(id -> !BotService.isBotId(id));
    }

    /** 가장 먼저 참가한 사람. 새 방장을 고를 때 쓴다. 사람이 없으면 예외. */
    private long firstHuman(Room room) {
        return room.memberIds().stream().filter(id -> !BotService.isBotId(id)).findFirst().orElseThrow();
    }

    /** 이 플레이어가 들어 있는 방. 어느 방에도 없으면 NOT_IN_ROOM. */
    public synchronized RoomInfo findRoomByPlayer(long playerId) {
        return toInfo(findRoom(roomIdOf(playerId)));
    }

    // ==== 참가 / 나가기 ====

    /**
     * 참가. 없는 방 ROOM_NOT_FOUND → 시작한 방 ROOM_ALREADY_STARTED
     * → 이미 어떤 방에 있음(시작한 방 포함) ALREADY_IN_ROOM → 가득 참 ROOM_FULL.
     */
    public synchronized RoomInfo join(String roomId, long playerId) {
        Room room = findRoom(roomId);
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new GameException(LobbyErrorCode.ROOM_ALREADY_STARTED);
        }
        requireNotInAnyRoom(playerId);
        if (room.isFull()) {
            throw new GameException(LobbyErrorCode.ROOM_FULL);
        }

        room.addMember(playerId, false);
        room.setNickname(playerId, nicknameOf(playerId));
        repository.save(room);
        return toInfo(room);
    }

    /**
     * 나가기. 참가하지 않았으면 NOT_IN_ROOM, 이미 시작한 방이면 ROOM_ALREADY_STARTED.
     * 방장이 나가면 가장 먼저 참가한 사람이 방장이 되고, 마지막 사람이 나가면 방이 삭제된다.
     * 방이 남아 있으면 true, 마지막 사람이 나가 방이 삭제됐으면 false.
     */
    public synchronized boolean leave(String roomId, long playerId) {
        Room room = findRoom(roomId);
        if (!room.hasMember(playerId)) {
            throw new GameException(LobbyErrorCode.NOT_IN_ROOM);
        }
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new GameException(LobbyErrorCode.ROOM_ALREADY_STARTED);
        }

        room.removeMember(playerId);
        if (!hasHuman(room)) {
            repository.deleteById(roomId);
            return false;
        }
        if (room.getHostId() == playerId) {
            long newHostId = firstHuman(room);
            room.setHostId(newHostId);
            room.setReady(newHostId, true); // 방장은 항상 준비 상태
        }
        repository.save(room);
        return true;
    }

    // ==== ROOM_UPDATED 내용 ====

    /**
     * ROOM_UPDATED 알림 내용을 만든다. 방 상태와 참가자 닉네임을 담는다. 없는 방이면 ROOM_NOT_FOUND.
     * 알림을 보내는 쪽이 입장, 퇴장, 준비, 방장 변경 뒤에 부른다.
     */
    public synchronized RoomUpdatedPayload roomUpdated(String roomId) {
        Room room = findRoom(roomId);
        List<RoomUpdatedPayload.PlayerSnapshot> players = room.memberIds().stream()
                .map(id -> new RoomUpdatedPayload.PlayerSnapshot(id, room.nicknameOf(id), room.isReady(id)))
                .toList();
        return new RoomUpdatedPayload(new RoomUpdatedPayload.RoomSnapshot(
                room.getRoomId(), room.getHostId(), room.getMapId(), room.getStatus(),
                room.getConfig(), players, room.getMaxPlayers()));
    }

    // ==== 방 설정 ====

    /**
     * 방장이 게임 설정과 최대 인원을 바꾼다.
     * 클라는 ROOM_CONFIG_REQUEST로 보내고, LobbyMessageHandler가 config와 maxPlayers를 꺼내 넘긴다.
     * config 값의 범위는 GameConfig가 만들어질 때 이미 검증된다.
     * 검증 순서: 없는 방 ROOM_NOT_FOUND → 시작한 방 ROOM_ALREADY_STARTED → 방장 아님 NOT_HOST
     * → 설정이 없음(null) INVALID_STATE → 최대 인원이 2~4가 아니거나 지금 인원보다 적음 INVALID_MAX_PLAYERS.
     * 실패하면 방은 그대로다.
     */
    public synchronized RoomInfo setRoomConfig(String roomId, long playerId, GameConfig config, int maxPlayers) {
        Room room = findRoom(roomId);
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new GameException(LobbyErrorCode.ROOM_ALREADY_STARTED);
        }
        if (room.getHostId() != playerId) {
            throw new GameException(LobbyErrorCode.NOT_HOST);
        }
        if (config == null) {
            throw new GameException(LobbyErrorCode.INVALID_STATE);
        }
        if (maxPlayers < GameConfig.MIN_PLAYERS || maxPlayers > GameConfig.MAX_PLAYERS || maxPlayers < room.size()) {
            throw new GameException(LobbyErrorCode.INVALID_MAX_PLAYERS);
        }

        room.setConfig(config);
        room.setMaxPlayers(maxPlayers);
        repository.save(room);
        return toInfo(room);
    }

    // ==== 준비 / 시작 ====

    /**
     * 준비 토글. 참가하지 않았으면 NOT_IN_ROOM, 시작한 방이면 ROOM_ALREADY_STARTED.
     * 방장은 항상 준비 상태라 바꾸려 하면 INVALID_STATE.
     */
    public synchronized RoomInfo setReady(String roomId, long playerId, boolean ready) {
        Room room = findRoom(roomId);
        if (!room.hasMember(playerId)) {
            throw new GameException(LobbyErrorCode.NOT_IN_ROOM);
        }
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new GameException(LobbyErrorCode.ROOM_ALREADY_STARTED);
        }
        if (room.getHostId() == playerId) {
            throw new GameException(LobbyErrorCode.INVALID_STATE);
        }

        room.setReady(playerId, ready);
        repository.save(room);
        return toInfo(room);
    }

    /**
     * 방장이 게임을 시작한다.
     * 검증 순서: 시작한 방 ROOM_ALREADY_STARTED → 방장 아님 NOT_HOST
     * → 2명 미만 NOT_ENOUGH_PLAYERS → 준비 안 한 사람 NOT_ALL_READY.
     * 성공하면 참가 순서대로 gameService.startGame을 부르고, 그 뒤에 상태를 PLAYING으로 바꾼다.
     * (startGame이 실패하면 방은 WAITING으로 남는다)
     */
    public synchronized RoomInfo start(String roomId, long playerId) {
        Room room = findRoom(roomId);
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new GameException(LobbyErrorCode.ROOM_ALREADY_STARTED);
        }
        if (room.getHostId() != playerId) {
            throw new GameException(LobbyErrorCode.NOT_HOST);
        }
        if (room.size() < GameConfig.MIN_PLAYERS) {
            throw new GameException(LobbyErrorCode.NOT_ENOUGH_PLAYERS);
        }
        if (!room.allReady()) {
            throw new GameException(LobbyErrorCode.NOT_ALL_READY);
        }

        Map<Long, String> nickNameMap = new HashMap<>();
        Set<Long> botIds = new HashSet<>();
        for (long memberId : room.memberIds()) {
            nickNameMap.put(memberId, room.nicknameOf(memberId));
            if (BotService.isBotId(memberId)) { botIds.add(memberId); }
        }

        gameService.startGame(roomId, room.memberIds(), nickNameMap, botIds, room.getConfig());
        room.setStatus(RoomStatus.PLAYING);
        repository.save(room);
        return toInfo(room);
    }

    // ==== 방 삭제 ====

    /**
     * 방을 지운다. 시작한 방(PLAYING)도 지운다.
     * 없는 방이면 ROOM_NOT_FOUND.
     */
    public synchronized void deleteRoom(String roomId) {
        findRoom(roomId);
        repository.deleteById(roomId);
    }

    /**
     * 게임이 끝나면(GameEndedEvent) 방을 대기 상태로 되돌려 같은 멤버로 다시 할 수 있게 한다.
     * 방장은 준비 상태를 유지하고 나머지 준비는 푼다.
     * 게임 요청을 처리하는 도중에 불리므로 방이 없어도 예외를 던지지 않는다.
     */
    @EventListener
    @Order(1)   // 방을 WAITING 상태로 먼저 되돌리고 그 후 연결 끊긴 멤버 정리(@Order(2)) 실행
    public synchronized void onGameEnded(GameEndedEvent event) {
        repository.findById(event.roomId()).ifPresent(room -> {
            room.setStatus(RoomStatus.WAITING);
            room.memberIds().stream()
                    .filter(id -> id != room.getHostId() && !BotService.isBotId(id))
                    .forEach(id -> room.setReady(id, false));
            repository.save(room);
        });
    }

    /**
     * 대기 중(WAITING)인 방에 있으면 내보낸다. 게임 중(PLAYING)이면 그대로 둔다.
     * 내보낸 뒤 방이 남아 있으면 그 방 id(남은 사람에게 ROOM_UPDATED를 보내야 함), 아니면 empty.
     */
    public synchronized Optional<String> leaveWaitingRoom(long playerId) {
        Optional<Room> room = repository.findRoomIdByPlayerId(playerId).flatMap(repository::findById);
        if (room.isEmpty() || room.get().getStatus() != RoomStatus.WAITING) {
            return Optional.empty();
        }
        String roomId = room.get().getRoomId();
        return leave(roomId, playerId) ? Optional.of(roomId) : Optional.empty();
    }

    /** 방의 멤버 id 목록 (복사본). 방이 없으면 빈 목록. */
    public synchronized List<Long> memberIdsOf(String roomId) {
        return repository.findById(roomId)
                .map(room -> List.copyOf(room.memberIds()))
                .orElse(List.of());
    }

    /**
     * 대기방에서 여러 명을 내보낸다. 게임이 끝난 뒤 접속이 끊긴 멤버를 정리할 때 쓴다.
     * 방장이 나가면 방장이 넘어가고, 모두 나가면 방이 삭제된다. 방이 남아 있으면 true.
     */
    public synchronized boolean removeMembers(String roomId, Collection<Long> playerIds) {
        boolean remains = repository.findById(roomId).isPresent();
        for (long playerId : playerIds) {
            if (!remains) {
                break;
            }
            remains = leave(roomId, playerId);
        }
        return remains;
    }

    // ==== 도우미 ====

    private Room findRoom(String roomId) {
        return repository.findById(roomId)
                .orElseThrow(() -> new GameException(LobbyErrorCode.ROOM_NOT_FOUND));
    }

    /** PlayerService에서 닉네임을 읽는다. 없으면 빈 문자열. */
    private String nicknameOf(long playerId) {
        String nickname = playerService.findMe(playerId).getNickname();
        return nickname == null ? "" : nickname;
    }

    /** 이 플레이어가 이미 들어가 있는 방이 있으면(시작한 방 포함) 거부한다. */
    private void requireNotInAnyRoom(long playerId) {
        if (repository.findRoomIdByPlayerId(playerId).isPresent()) {
            throw new GameException(LobbyErrorCode.ALREADY_IN_ROOM);
        }
    }

    private RoomInfo toInfo(Room room) {
        List<RoomPlayer> players = room.memberIds().stream()
                .map(id -> new RoomPlayer(id, room.isReady(id)))
                .toList();
        return new RoomInfo(room.getRoomId(), room.getHostId(), room.getMapId(), room.getMaxPlayers(), players, room.getStatus());
    }


}
