package com.sparta.ourmarbleserver.game.service;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.sparta.ourmarbleserver.auth.service.PlayerService;
import com.sparta.ourmarbleserver.game.event.GameEndedEvent;
import com.sparta.ourmarbleserver.game.event.GameEndedEvent.PlayerResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 게임이 끝나면 사람 플레이어의 전적을 올린다. 승리는 최종 등수 1위뿐이고, 나머지는 패배다.
 * (대신 진행으로 끝난 사람도 등수로만 판단한다: 1위면 승리, 아니면 패배. 봇은 계정이 없어 기록하지 않는다)
 * 이벤트는 요청 처리 도중(그 방의 리스를 잡은 채) 나가므로, 여기서는 DB 저장을 별도 스레드에 맡기기만 하고 바로 돌아온다.
 * 한 명의 저장이 실패해도 다른 사람의 저장과 게임 종료 흐름에는 영향을 주지 않는다. (로그만 남긴다)
 */
@Slf4j
@Component
public class PlayRecordRecorder {

    private final PlayerService playerService;
    private final Executor executor;

    @Autowired
    public PlayRecordRecorder(PlayerService playerService) {
        this(playerService, newDaemonExecutor());
    }

    /** 테스트에서 저장을 같은 스레드에서 바로 실행하도록 실행기를 넘긴다. */
    PlayRecordRecorder(PlayerService playerService, Executor executor) {
        this.playerService = playerService;
        this.executor = executor;
    }

    @EventListener
    public void onGameEnded(GameEndedEvent event) {
        for (PlayerResult result : event.results()) {
            if (result.bot()) { continue; }
            executor.execute(() -> record(event.roomId(), result));
        }
    }

    private void record(String roomId, PlayerResult result) {
        boolean won = result.finalRank() == 1;
        try {
            playerService.updatePlayRecord(result.playerId(), won);
        } catch (RuntimeException e) {
            log.warn("[전적] 저장 실패: room={} player={} won={}", roomId, result.playerId(), won, e);
        }
    }

    /** 서버가 꺼질 때 종료를 막지 않도록 데몬 스레드 하나로 순서대로 저장한다. */
    private static Executor newDaemonExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "play-record");
            thread.setDaemon(true);
            return thread;
        });
    }
}