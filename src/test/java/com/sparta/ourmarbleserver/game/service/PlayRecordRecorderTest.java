package com.sparta.ourmarbleserver.game.service;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sparta.ourmarbleserver.auth.service.PlayerService;
import com.sparta.ourmarbleserver.game.event.GameEndedEvent;
import com.sparta.ourmarbleserver.game.event.GameEndedEvent.PlayerResult;

/** 게임이 끝났을 때 전적이 규칙대로(1위만 승리, 봇 제외) 올라가는지 확인한다. */
class PlayRecordRecorderTest {

    private final PlayerService playerService = mock(PlayerService.class);
    private final PlayRecordRecorder recorder = new PlayRecordRecorder(playerService, Runnable::run);   // 같은 스레드에서 바로 실행

    private static GameEndedEvent event(PlayerResult... results) {
        return new GameEndedEvent("r1", List.of(results));
    }

    @Test
    void 일등만_승리로_기록하고_나머지는_패배로_기록한다() {
        recorder.onGameEnded(event(
                new PlayerResult(1L, 2, false),
                new PlayerResult(2L, 1, false),
                new PlayerResult(3L, 3, false)));

        verify(playerService).updatePlayRecord(2L, true);
        verify(playerService).updatePlayRecord(1L, false);
        verify(playerService).updatePlayRecord(3L, false);
    }

    @Test
    void 봇이_일등이면_사람은_모두_패배이고_봇은_기록하지_않는다() {
        recorder.onGameEnded(event(
                new PlayerResult(99L, 1, true),
                new PlayerResult(1L, 2, false),
                new PlayerResult(2L, 3, false)));

        verify(playerService).updatePlayRecord(1L, false);
        verify(playerService).updatePlayRecord(2L, false);
        verify(playerService, never()).updatePlayRecord(eq(99L), anyBoolean());
    }

    @Test
    void 한_명의_저장이_실패해도_나머지는_기록된다() {
        doThrow(new RuntimeException("DB 오류")).when(playerService).updatePlayRecord(2L, false);

        recorder.onGameEnded(event(
                new PlayerResult(1L, 1, false),
                new PlayerResult(2L, 2, false),
                new PlayerResult(3L, 3, false)));

        verify(playerService).updatePlayRecord(1L, true);
        verify(playerService).updatePlayRecord(2L, false);
        verify(playerService).updatePlayRecord(3L, false);
    }

    @Test
    void 결과가_없는_이벤트는_아무것도_기록하지_않는다() {
        recorder.onGameEnded(new GameEndedEvent("r1"));

        verifyNoInteractions(playerService);
    }
}