package com.sparta.ourmarbleserver.lobby.service;

import com.sparta.ourmarbleserver.auth.dto.PlayerResponse;
import com.sparta.ourmarbleserver.auth.service.PlayerService;
import com.sparta.ourmarbleserver.bot.service.BotService;
import com.sparta.ourmarbleserver.game.event.GameEndedEvent;
import com.sparta.ourmarbleserver.game.service.GameService;
import com.sparta.ourmarbleserver.game.state.GameConfig;
import com.sparta.ourmarbleserver.global.exception.GameException;
import com.sparta.ourmarbleserver.global.protocol.LobbyErrorCode;
import com.sparta.ourmarbleserver.lobby.dto.*;
import com.sparta.ourmarbleserver.lobby.state.Room;
import com.sparta.ourmarbleserver.lobby.state.RoomLock;
import com.sparta.ourmarbleserver.lobby.state.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 로비 도메인 서비스. 웹소켓 요청을 받는 쪽(LobbyMessageHandler)이 이 클래스의 메서드만 호출한다.
 * playerId는 접속할 때 확인된 값이 전송 계층에서 넘어온다.
 * 규칙에 어긋난 요청은 GameException(LobbyErrorCode.*)을 던지고, 전송 계층이 LOBBY_ERROR로 바꿔 보낸다.
 * 방을 바꾸는 메서드는 그 방의 락(RoomLock) 안에서 "읽고 → 검증 → 저장"한다. 같은 서버 안이든 서버 사이든 이 락이 막는다.
 * 서비스 전체를 synchronized로 잠그지 않는다. (잠그면 방이 달라도 모든 요청이 한 줄로 서고, 락 대기와 DB·Redis 조회가 그 줄을 막는다)
 * 읽기만 하는 메서드는 락 없이 읽는다. (방은 읽을 때마다 새로 만든 복사본이다)
 * 게임이 끝나면 게임 쪽이 GameEndedEvent를 내보내고, onGameEnded가 받아 방을 대기(WAITING) 상태로 되돌린다.
 * 멤버는 그대로 두고 방장이 아닌 사람의 준비는 푼다.
 * (GameService가 LobbyService를 직접 부르면 서로를 참조해서 서버가 뜨지 않으므로 이벤트로 받는다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LobbyService {

    private final RoomRepository repository;
    private final GameService gameService;
    private final PlayerService playerService;
    private final BotService botService;
    private final RoomLock roomLock;

    // ==== 방 만들기 / 조회 ====

    /**
     * 방을 만든다. 인원수는 게임 최대 인원(GameConfig.MAX_PLAYERS), 맵은 기본 맵(GameConfig.DEFAULT_MAP_ID)으로 정한다.
     * (CREATE_ROOM 요청에는 본문이 없다)
     */
    public RoomInfo createRoom(long playerId) {
        return createRoom(playerId, GameConfig.DEFAULT_MAP_ID, GameConfig.MAX_PLAYERS);
    }

    /**
     * 방을 만든다. 만든 사람이 방장이고 자동으로 참가한다.
     * maxPlayers가 2~4가 아니면 INVALID_STATE, 이미 어떤 방에든(시작한 방 포함) 있으면 ALREADY_IN_ROOM.
     * mapId는 검증하지 않는다. (맵 목록이 서버에 없다)
     */
    public RoomInfo createRoom(long playerId, int mapId, int maxPlayers) {
        if (maxPlayers < GameConfig.MIN_PLAYERS || maxPlayers > GameConfig.MAX_PLAYERS) {
            throw new GameException(LobbyErrorCode.INVALID_STATE);
        }
        requireNotInAnyRoom(playerId);
        PlayerResponse profile = playerService.findMe(playerId);   // DB 조회는 락 밖에서
        String nickname = nicknameOf(profile);

        String roomId = repository.nextRoomId();
        return roomLock.call(roomId, () -> {
            Room room = new Room(roomId, playerId, mapId, maxPlayers);
            room.addMember(playerId, true);
            room.setNickname(playerId, nickname);
            room.setPortrait(playerId, profile.getPortraitId());
            repository.save(room);
            return toInfo(room);
        });
    }

    /** 대기 중(WAITING)인 방 목록 */
    public List<RoomInfo> listRooms() {
        return repository.findAll().stream()
                .filter(room -> room.getStatus() == RoomStatus.WAITING)
                .map(this::toInfo)
                .toList();
    }

    /** ROOM_LIST 알림 내용. 대기 중(WAITING)인 방만 담는다. */
    public RoomListPayload roomList() {
        List<RoomListPayload.RoomSummary> rooms = repository.findAll().stream()
                .filter(room -> room.getStatus() == RoomStatus.WAITING)
                .map(room -> new RoomListPayload.RoomSummary(
                        room.getRoomId(), room.nicknameOf(room.getHostId()), room.getMapId(), room.size(), room.getMaxPlayers()))
                .toList();
        return new RoomListPayload(rooms);
    }

    /** 방 한 개 조회. 없으면 ROOM_NOT_FOUND. */
    public RoomInfo getRoom(String roomId) {
        return toInfo(findRoom(roomId));
    }

    /** 방 한 개 조회. 없으면 empty. (게임 상태에 방 정보를 붙일 때처럼 방이 없어도 넘어갈 수 있을 때 쓴다) */
    public Optional<RoomInfo> findRoomInfo(String roomId) {
        return repository.findById(roomId).map(this::toInfo);
    }

    /**
     * 이 플레이어가 들어 있는 방 id. 어느 방에도 없으면 NOT_IN_ROOM.
     * 요청에는 roomId가 실리지 않아서(LEAVE_ROOM, SET_READY, START_GAME) 요청한 사람의 방을 이걸로 찾는다.
     * 한 플레이어는 방 하나에만 들어갈 수 있다. (시작한 방 포함)
     */
    public String roomIdOf(long playerId) {
        return repository.findRoomIdByPlayerId(playerId)
                .orElseThrow(() -> new GameException(LobbyErrorCode.NOT_IN_ROOM));
    }

    /** 게임 중(PLAYING)인 방에 있으면 그 방 id. 재접속 시 게임 화면으로 돌려보낼 때 쓴다. */
    public Optional<String> playingRoomIdOf(long playerId) {
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
    public RoomInfo findRoomByPlayer(long playerId) {
        return toInfo(findRoom(roomIdOf(playerId)));
    }

    // ==== 참가 / 나가기 ====

    /**
     * 참가. 없는 방 ROOM_NOT_FOUND → 시작한 방 ROOM_ALREADY_STARTED
     * → 이미 어떤 방에 있음(시작한 방 포함) ALREADY_IN_ROOM → 가득 참 ROOM_FULL.
     */
    public RoomInfo join(String roomId, long playerId) {
        PlayerResponse profile = playerService.findMe(playerId); // DB 조회는 방 락을 잡기 전에 한다. (락을 쥔 채로 DB를 기다리지 않게)
        String nickname = nicknameOf(profile);

        return roomLock.call(roomId, () -> {
            Room room = findRoom(roomId);
            if (room.getStatus() != RoomStatus.WAITING) {
                throw new GameException(LobbyErrorCode.ROOM_ALREADY_STARTED);
            }
            requireNotInAnyRoom(playerId);
            if (room.isFull()) {
                throw new GameException(LobbyErrorCode.ROOM_FULL);
            }

            room.addMember(playerId, false);
            room.setNickname(playerId, nickname);
            room.setPortrait(playerId, profile.getPortraitId());
            repository.save(room);
            return toInfo(room);
        });
    }

    /**
     * 나가기. 참가하지 않았으면 NOT_IN_ROOM, 이미 시작한 방이면 ROOM_ALREADY_STARTED.
     * 방장이 나가면 가장 먼저 참가한 사람이 방장이 되고, 마지막 사람이 나가면 방이 삭제된다.
     * 방이 남아 있으면 true, 마지막 사람이 나가 방이 삭제됐으면 false.
     */
    public boolean leave(String roomId, long playerId) {
        return roomLock.call(roomId, () -> leaveInLock(roomId, playerId));
    }

    /** 나가기 본체. 반드시 그 방의 락 안에서 부른다. (락 안에서 leave를 부르면 자기 락을 기다리게 된다) */
    public boolean leaveInLock(String roomId, long playerId) {
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
     * ROOM_UPDATED 알림 내용을 만든다. 방 상태와 참가자 닉네임, 초상화(사람만)를 담는다. 없는 방이면 ROOM_NOT_FOUND.
     * 알림을 보내는 쪽이 입장, 퇴장, 준비, 방장 변경 뒤에 부른다.
     */
    public RoomUpdatedPayload roomUpdated(String roomId) {
        Room room = findRoom(roomId);
        List<RoomUpdatedPayload.PlayerSnapshot> players = room.memberIds().stream()
                .map(id -> new RoomUpdatedPayload.PlayerSnapshot(id, room.nicknameOf(id), room.isReady(id), BotService.isBotId(id),
                        BotService.isBotId(id) ? null : room.portraitOf(id)))
                .toList();
        return new RoomUpdatedPayload(new RoomUpdatedPayload.RoomSnapshot(
                room.getRoomId(), room.getHostId(), room.getMapId(), room.getStatus(),
                room.getConfig(), players, room.getMaxPlayers()));
    }

    // ==== 방 설정 ====

    /**
     * 방장이 게임 설정과 최대 인원, 봇 수를 바꾼다.
     * 클라는 ROOM_CONFIG_REQUEST로 보내고, LobbyMessageHandler가 config와 maxPlayers를 꺼내 넘긴다.
     * config 값의 범위는 GameConfig가 만들어질 때 이미 검증된다.
     * 검증 순서: 없는 방 ROOM_NOT_FOUND → 시작한 방 ROOM_ALREADY_STARTED → 방장 아님 NOT_HOST
     * → 설정이 없음(null) INVALID_STATE
     * → 최대 인원이 2~4가 아니거나 지금 사람 수보다 적음 INVALID_MAX_PLAYERS.
     * → 봇 수가 음수이거나 사람 + 봇이 최대 인원을 넘음 INVALID_BOT_COUNT.
     * 실패하면 방은 그대로다.
     */
    public RoomInfo setRoomConfig(String roomId, long playerId, GameConfig config, int maxPlayers, int botCount) {
        return roomLock.call(roomId, () -> {
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

            int humans = humanCount(room);
            if (maxPlayers < GameConfig.MIN_PLAYERS || maxPlayers > GameConfig.MAX_PLAYERS || maxPlayers < humans) {
                throw new GameException(LobbyErrorCode.INVALID_MAX_PLAYERS);
            }
            if (botCount < 0 || humans + botCount > maxPlayers) {
                throw new GameException(LobbyErrorCode.INVALID_BOT_COUNT);
            }

            room.setConfig(config);
            room.setMaxPlayers(maxPlayers);
            syncBots(room, botCount);
            repository.save(room);
            return toInfo(room);
        });
    }

    // 방에 있는 사람 수 (봇 제외)
    private int humanCount(Room room) {
        return (int) room.memberIds().stream().filter(id -> !BotService.isBotId(id)).count();
    }

    private void syncBots(Room room, int target) {
        List<Long> bots = room.memberIds().stream().filter(BotService::isBotId).toList();

        for(int i = bots.size() - 1; i >= target; i--) {    // 많으면 제거
            room.removeMember(bots.get(i));
        }

        for(int i = bots.size(); i < target; i++) {         // 부족하면 추가
            List<String> used = room.memberIds().stream().map(room::nicknameOf).toList();
            long botId = botService.nextBotId();
            room.addMember(botId, true);
            room.setNickname(botId, botService.nextNickname(used));
        }
    }

    // ==== 준비 / 시작 ====

    /**
     * 준비 토글. 참가하지 않았으면 NOT_IN_ROOM, 시작한 방이면 ROOM_ALREADY_STARTED.
     * 방장은 항상 준비 상태라 바꾸려 하면 INVALID_STATE.
     */
    public RoomInfo setReady(String roomId, long playerId, boolean ready) {
        return roomLock.call(roomId, () -> {
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
        });
    }

    // ==== 초상화 ====

    /** 초상화 번호의 범위. 클라이언트에 그림이 8장 있고 번호는 0~7이다. */
    public static final int MIN_PORTRAIT_ID = 0;
    public static final int MAX_PORTRAIT_ID = 7;

    /**
     * 내 초상화를 바꾼다. 번호가 0~7이 아니면 INVALID_PORTRAIT.
     * 계정(DB)에 저장하고, 방에 들어 있으면 방에 저장된 값도 바꾼다. (게임이 끝나 방이 대기 상태로 돌아왔을 때 새 값이 보이도록)
     * 방 전원에게 ROOM_UPDATED를 보내야 하는 경우(대기 중인 방에 있음)에만 그 방 id를 돌려준다.
     * 방에 없거나 게임 중(PLAYING)이면 저장만 하고 empty. (게임 중에는 방 전원에게 알리지 않고, 게임 상태도 바꾸지 않는다)
     */
    public Optional<String> setPortrait(long playerId, int portraitId) {
        if (portraitId < MIN_PORTRAIT_ID || portraitId > MAX_PORTRAIT_ID) {
            throw new GameException(LobbyErrorCode.INVALID_PORTRAIT);
        }
        playerService.updatePortrait(playerId, portraitId);   // DB 저장은 방 락 밖에서

        Optional<String> roomId = repository.findRoomIdByPlayerId(playerId);
        if (roomId.isEmpty()) {
            return Optional.empty();
        }
        return roomLock.call(roomId.get(), () -> repository.findById(roomId.get())
                .filter(room -> room.hasMember(playerId))
                .flatMap(room -> {
                    room.setPortrait(playerId, portraitId);
                    repository.save(room);
                    return room.getStatus() == RoomStatus.WAITING ? Optional.of(room.getRoomId()) : Optional.<String>empty();
                }));
    }

    /**
     * 방장이 게임을 시작한다.
     * 검증 순서: 시작한 방 ROOM_ALREADY_STARTED → 방장 아님 NOT_HOST
     * → 2명 미만 NOT_ENOUGH_PLAYERS → 준비 안 한 사람 NOT_ALL_READY.
     * 성공하면 참가 순서대로 gameService.startGame을 부르고, 그 뒤에 상태를 PLAYING으로 바꾼다.
     * (startGame이 실패하면 방은 WAITING으로 남는다)
     */
    public RoomInfo start(String roomId, long playerId) {
        return roomLock.call(roomId, () -> {
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
            Map<Long, Integer> portraitMap = new HashMap<>();   // 시작 시점에 저장돼 있던 값. 고르지 않은 사람과 봇은 없다.
            for (long memberId : room.memberIds()) {
                nickNameMap.put(memberId, room.nicknameOf(memberId));
                if (BotService.isBotId(memberId)) {
                    botIds.add(memberId);
                } else if (room.portraitOf(memberId) != null) {
                    portraitMap.put(memberId, room.portraitOf(memberId));
                }
            }

            gameService.startGame(roomId, room.memberIds(), nickNameMap, botIds, portraitMap, room.getConfig());
            room.setStatus(RoomStatus.PLAYING);
            repository.save(room);
            return toInfo(room);
        });
    }

    // ==== 방 삭제 ====

    /**
     * 방을 지운다. 시작한 방(PLAYING)도 지운다.
     * 없는 방이면 ROOM_NOT_FOUND.
     */
    public void deleteRoom(String roomId) {
        roomLock.run(roomId, () -> {
            findRoom(roomId);
            repository.deleteById(roomId);
        });
    }

    /**
     * 게임이 끝나면(GameEndedEvent) 방을 대기 상태로 되돌려 같은 멤버로 다시 할 수 있게 한다.
     * 방장은 준비 상태를 유지하고 나머지 준비는 푼다.
     * 게임 요청을 처리하는 도중에 불리므로 방이 없어도 예외를 던지지 않는다.
     */
    @EventListener
    @Order(1)   // 방을 WAITING 상태로 먼저 되돌리고 그 후 연결 끊긴 멤버 정리(@Order(2)) 실행
    public void onGameEnded(GameEndedEvent event) {
        try {
            roomLock.run(event.roomId(), () -> repository.findById(event.roomId()).ifPresent(room -> {
                room.setStatus(RoomStatus.WAITING);
                room.memberIds().stream()
                        .filter(id -> id != room.getHostId() && !BotService.isBotId(id))
                        .forEach(id -> room.setReady(id, false));
                repository.save(room);
            }));
        } catch (RuntimeException e) {
            // 게임 요청을 처리하는 스레드에서 불리므로, 실패해도 뒤 리스너(끊긴 멤버 정리, 게임 상태 삭제)가 돌게 막는다
            log.error("[로비] 게임 종료 후 방 되돌리기 실패: room={}", event.roomId(), e);
        }
    }

    /**
     * 대기 중(WAITING)인 방에 있으면 내보낸다. 게임 중(PLAYING)이면 그대로 둔다.
     * 내보낸 뒤 방이 남아 있으면 그 방 id(남은 사람에게 ROOM_UPDATED를 보내야 함), 아니면 empty.
     */
    public Optional<String> leaveWaitingRoom(long playerId) {
        Optional<String> found = repository.findRoomIdByPlayerId(playerId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        String roomId = found.get();
        return roomLock.call(roomId, () -> {
            // 락을 잡기 전에 다른 서버가 방을 바꿨을 수 있으니, 락 안에서 다시 읽어 확인한다
            Optional<Room> room = repository.findById(roomId);
            if (room.isEmpty() || room.get().getStatus() != RoomStatus.WAITING || !room.get().hasMember(playerId)) {
                return Optional.<String>empty();
            }
            return leaveInLock(roomId, playerId) ? Optional.of(roomId) : Optional.<String>empty();
        });
    }

    /** 방의 멤버 id 목록 (복사본). 방이 없으면 빈 목록. */
    public List<Long> memberIdsOf(String roomId) {
        return repository.findById(roomId)
                .map(room -> List.copyOf(room.memberIds()))
                .orElse(List.of());
    }

    /** 대기 중(WAITING)인 방 id 목록. 유령 멤버 청소에 쓴다. */
    public List<String> waitingRoomIds() {
        return repository.findAll().stream()
                .filter(room -> room.getStatus() == RoomStatus.WAITING)
                .map(Room::getRoomId)
                .toList();
    }

    /**
     * 대기방에서 여러 명을 내보낸다. 게임 종료 후 끊긴 멤버 정리, 유령 멤버 청소에 쓴다.
     * 이미 나간 사람이나 대기 중이 아닌 방은 건너뛴다. (확인과 정리 사이에 다른 서버가 방을 바꿨을 수 있다)
     * 방장이 나가면 방장이 넘어가고, 모두 나가면 방이 삭제된다. 방이 남아 있으면 true.
     */
    public boolean removeMembers(String roomId, Collection<Long> playerIds) {
        return roomLock.call(roomId, () -> {
            for (long playerId : playerIds) {
                Optional<Room> room = repository.findById(roomId);
                if (room.isEmpty()) {
                    return false;
                }
                if (room.get().getStatus() != RoomStatus.WAITING || !room.get().hasMember(playerId)) {
                    continue;
                }
                if (!leaveInLock(roomId, playerId)) {
                    return false;
                }
            }
            return repository.findById(roomId).isPresent();
        });
    }

    // ==== 도우미 ====

    private Room findRoom(String roomId) {
        return repository.findById(roomId)
                .orElseThrow(() -> new GameException(LobbyErrorCode.ROOM_NOT_FOUND));
    }

    /** 계정 정보에서 닉네임을 꺼낸다. 없으면 빈 문자열. */
    private static String nicknameOf(PlayerResponse profile) {
        String nickname = profile.getNickname();
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