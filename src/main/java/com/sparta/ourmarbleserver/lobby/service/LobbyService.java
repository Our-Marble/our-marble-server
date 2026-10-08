package com.sparta.ourmarbleserver.lobby.service;

import com.sparta.ourmarbleserver.game.service.GameService;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.ErrorCode;
import com.sparta.ourmarbleserver.lobby.dto.RoomInfo;
import com.sparta.ourmarbleserver.lobby.dto.RoomPlayer;
import com.sparta.ourmarbleserver.lobby.dto.RoomStatus;
import com.sparta.ourmarbleserver.lobby.state.Room;
import com.sparta.ourmarbleserver.lobby.state.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 로비 도메인 서비스. 웹소켓 요청을 받는 쪽(MessageHandler)이 이 클래스의 메서드만 호출한다.
 * playerId는 접속할 때 확인된 값이 전송 계층에서 넘어온다.
 * 규칙에 어긋난 요청은 GameException(ErrorCode.*)을 던지고, 전송 계층이 ERROR로 바꿔 보낸다.
 * 방 하나를 여러 요청이 동시에 바꿀 수 있어서 모든 메서드를 synchronized로 직렬 처리한다.
 *
 * 게임이 끝나면 게임 쪽에서 deleteRoom을 불러 방을 지운다.
 * TODO: deleteRoom을 부르는 곳(게임 종료 처리)이 아직 없다. (PLAYING 방은 그대로 남는다)
 */
@Service
@RequiredArgsConstructor
public class LobbyService {
    public static final int MIN_PLAYERS=2;
    public static final int MAX_PLAYERS=4;

    private final RoomRepository repository;
    private final GameService gameService;
    private final AtomicLong roomSequence = new AtomicLong();

    // ==== 방 만들기 / 조회 ====

    /**
     * 방을 만든다. 만든 사람이 방장이고 자동으로 참가한다.
     * maxPlayers가 2~4가 아니면 INVALID_STATE, 이미 대기 중인 방에 있으면 ALREADY_IN_ROOM.
     * mapId는 검증하지 않는다. (맵 목록이 서버에 없다)
     */
    public synchronized RoomInfo createRoom(long playerId, int mapId, int maxPlayers) {
        if (maxPlayers < MIN_PLAYERS || maxPlayers > MAX_PLAYERS) {
            throw new GameException(ErrorCode.INVALID_STATE);
        }
        requireNotInWaitingRoom(playerId);

        Room room = new Room("r_" + roomSequence.incrementAndGet(), playerId, mapId, maxPlayers);
        room.addMember(playerId, true);
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

    /** 방 한 개 조회. 없으면 ROOM_NOT_FOUND. 클라 폴링용이다. */
    public synchronized RoomInfo getRoom(String roomId) {
        return toInfo(findRoom(roomId));
    }

    // ==== 참가 / 나가기 ====

    /**
     * 참가. 없는 방 ROOM_NOT_FOUND → 시작한 방 ROOM_ALREADY_STARTED
     * → 이미 대기 중인 방에 있음 ALREADY_IN_ROOM → 가득 참 ROOM_FULL.
     */
    public synchronized RoomInfo join(String roomId, long playerId) {
        Room room = findRoom(roomId);
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new GameException(ErrorCode.ROOM_ALREADY_STARTED);
        }
        requireNotInWaitingRoom(playerId);
        if (room.isFull()) {
            throw new GameException(ErrorCode.ROOM_FULL);
        }

        room.addMember(playerId, false);
        repository.save(room);
        return toInfo(room);
    }

    /**
     * 나가기. 참가하지 않았으면 NOT_IN_ROOM, 이미 시작한 방이면 ROOM_ALREADY_STARTED.
     * 방장이 나가면 가장 먼저 참가한 사람이 방장이 되고, 마지막 사람이 나가면 방이 삭제된다.
     */
    public synchronized void leave(String roomId, long playerId) {
        Room room = findRoom(roomId);
        if (!room.hasMember(playerId)) {
            throw new GameException(ErrorCode.NOT_IN_ROOM);
        }
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new GameException(ErrorCode.ROOM_ALREADY_STARTED);
        }

        room.removeMember(playerId);
        if (room.isEmpty()) {
            repository.deleteById(roomId);
            return;
        }
        if (room.getHostId() == playerId) {
            long newHostId = room.memberIds().getFirst();
            room.setHostId(newHostId);
            room.setReady(newHostId, true); // 방장은 항상 준비 상태
        }
        repository.save(room);
    }

    // ==== 준비 / 시작 ====

    /**
     * 준비 토글. 참가하지 않았으면 NOT_IN_ROOM, 시작한 방이면 ROOM_ALREADY_STARTED.
     * 방장은 항상 준비 상태라 바꾸려 하면 INVALID_STATE.
     */
    public synchronized RoomInfo setReady(String roomId, long playerId, boolean ready) {
        Room room = findRoom(roomId);
        if (!room.hasMember(playerId)) {
            throw new GameException(ErrorCode.NOT_IN_ROOM);
        }
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new GameException(ErrorCode.ROOM_ALREADY_STARTED);
        }
        if (room.getHostId() == playerId) {
            throw new GameException(ErrorCode.INVALID_STATE);
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
            throw new GameException(ErrorCode.ROOM_ALREADY_STARTED);
        }
        if (room.getHostId() != playerId) {
            throw new GameException(ErrorCode.NOT_HOST);
        }
        if (room.size() < MIN_PLAYERS) {
            throw new GameException(ErrorCode.NOT_ENOUGH_PLAYERS);
        }
        if (!room.allReady()) {
            throw new GameException(ErrorCode.NOT_ALL_READY);
        }

        gameService.startGame(roomId, room.memberIds());
        room.setStatus(RoomStatus.PLAYING);
        repository.save(room);
        return toInfo(room);
    }

    // ==== 방 삭제 ====

    /**
     * 방을 지운다. 게임이 끝났을 때 게임 쪽에서 부른다. 시작한 방(PLAYING)도 지운다.
     * 없는 방이면 ROOM_NOT_FOUND.
     */
    public synchronized void deleteRoom(String roomId) {
        findRoom(roomId);
        repository.deleteById(roomId);
    }

    // ==== 도우미 ====

    private Room findRoom(String roomId) {
        return repository.findById(roomId)
                .orElseThrow(() -> new GameException(ErrorCode.ROOM_NOT_FOUND));
    }

    /** 대기 중인 방 중 이 플레이어가 이미 들어가 있는 방이 있으면 거부한다. */
    private void requireNotInWaitingRoom(long playerId) {
        boolean alreadyIn = repository.findAll().stream()
                .anyMatch(room -> room.getStatus() == RoomStatus.WAITING && room.hasMember(playerId));
        if (alreadyIn) {
            throw new GameException(ErrorCode.ALREADY_IN_ROOM);
        }
    }

    private RoomInfo toInfo(Room room) {
        List<RoomPlayer> players = room.memberIds().stream()
                .map(id -> new RoomPlayer(id, room.isReady(id)))
                .toList();
        return new RoomInfo(room.getRoomId(), room.getHostId(), room.getMapId(), room.getMaxPlayers(), players, room.getStatus());
    }


}
