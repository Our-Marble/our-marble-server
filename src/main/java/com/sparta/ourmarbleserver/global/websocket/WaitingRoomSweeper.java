package com.sparta.ourmarbleserver.global.websocket;

import com.sparta.ourmarbleserver.bot.service.BotService;
import com.sparta.ourmarbleserver.global.protocol.MessageType;
import com.sparta.ourmarbleserver.lobby.service.LobbyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 대기방에 남은 유령 멤버를 주기적으로 내보낸다.
 * 서버가 내려가면 그 서버에 붙어 있던 대기방 멤버는 정리해 줄 계기가 없다. (재접속하지 않으면 영원히 남는다)
 * 접속 기록(presence)이 없는 사람 = TTL(60초)이 지나도록 어느 서버에도 없는 사람이라, 잠깐 끊긴 사람은 건드리지 않는다.
 * 모든 서버가 돌리지만 방 락과 removeMembers의 "이미 나간 사람 건너뛰기" 덕분에 겹쳐도 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingRoomSweeper {

    static final long SWEEP_INTERVAL_MILLIS = 30_000;

    private final SessionRegistry sessionRegistry;
    private final LobbyService lobbyService;
    private final WebSocketMessageSender sender;

    // 서버가 막 켜졌을 때 재접속 중인 사람을 바로 치우지 않게, 첫 청소도 한 주기 뒤에 한다
    @Scheduled(fixedDelay = SWEEP_INTERVAL_MILLIS, initialDelay = SWEEP_INTERVAL_MILLIS)
    public void sweep() {
        List<String> roomIds;
        try {
            roomIds = lobbyService.waitingRoomIds();
        } catch (RuntimeException e) {
            log.warn("[청소] 대기방 목록 조회 실패: {}", e.getMessage());
            return;
        }
        for (String roomId : roomIds) {
            try {
                sweepRoom(roomId);
            } catch (RuntimeException e) {
                log.warn("[청소] 실패: room={} {}", roomId, e.getMessage());   // 한 방이 실패해도 다음 방은 계속
            }
        }
    }

    private void sweepRoom(String roomId) {
        List<Long> gone = lobbyService.memberIdsOf(roomId).stream()
                .filter(id -> !BotService.isBotId(id))
                .filter(id -> !sessionRegistry.isConnected(id))
                .toList();
        if (gone.isEmpty()) {
            return;
        }
        log.info("[청소] 접속 기록이 없는 대기방 멤버 정리: room={} players={}", roomId, gone);
        if (lobbyService.removeMembers(roomId, gone)) {
            sender.sendToRoom(roomId, MessageType.ROOM_UPDATED, lobbyService.roomUpdated(roomId));
        }
    }
}