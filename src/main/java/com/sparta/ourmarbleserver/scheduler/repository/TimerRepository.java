package com.sparta.ourmarbleserver.scheduler.repository;

import com.sparta.ourmarbleserver.game.state.GameState;

import java.util.List;

/**
 * 방별 '다음 요청 수신 마감시간'과 AFK 플레이어를 보관하는 저장소.
 * 서버는 RedisTimerRepository, 테스트는 InMemoryTimerRepository를 쓴다.
 *
 * 마감시간 하나와 리스(처리 중 표시) 하나가 방마다 있다.
 * - 마감시간: 이 시각까지 요청이 오지 않으면 서버가 대신 진행한다.
 * - 리스: 요청을 처리하는 동안 다른 요청(다른 서버 포함)과 대신 진행이 끼어들지 못하게 잡아 두는 표시.
 *   리스를 잡으면 마감시간을 '지금 + 리스 시간'으로 늘려 둔다. 처리 중에 서버가 죽으면 리스 시간이 지난 뒤
 *   다른 서버가 마감이 지난 것으로 보고 대신 진행한다. (처리 결과가 저장되지 않았으므로 이전 상태에서 다시 진행)
 * 시각은 구현이 정한 한 시계(Redis는 Redis 서버 시각)로만 잰다. 서버마다 시계가 조금 달라도 어긋나지 않게 하려는 것이다.
 */
public interface TimerRepository {

    /** 사람의 요청이 리스를 잡으려 한 결과 */
    enum ClaimStatus {
        /** 리스를 잡았다 */
        OK,
        /** 마감시간이 이미 지났다 (서버가 대신 진행할 차례) */
        EXPIRED,
        /** 다른 요청이나 대신 진행이 처리 중이다 */
        BUSY
    }

    /**
     * @param status           결과
     * @param previousDeadline 리스를 잡기 전의 마감시간(ms). 요청이 실패해 리스를 되돌릴 때 이 값으로 되돌린다.
     *                         마감시간이 없던 방이면 0.
     */
    record Claim(ClaimStatus status, long previousDeadline) {
    }

    /**
     * 사람의 요청: 마감시간이 아직 안 지났고 처리 중인 리스가 없으면 리스를 잡고 마감시간을 '지금 + leaseMillis'로 늘린다.
     * 마감시간이 없는 방(타이머를 시작하지 않은 방)은 리스만 잡는다.
     */
    Claim claimBeforeDeadline(String roomId, String token, long leaseMillis);

    /**
     * 서버의 대신 진행: 마감시간이 지났으면 리스를 잡고 마감시간을 '지금 + leaseMillis'로 늘린다.
     * 이미 리스가 있어도 마감시간이 지났다면 그 리스의 주인(죽은 서버)은 끝난 것으로 보고 빼앗는다.
     * @return 잡았으면 true, 아직 마감 전이거나 마감시간이 없으면 false
     */
    boolean claimExpired(String roomId, String token, long leaseMillis);

    /**
     * 리스를 풀고 새 마감시간('지금 + delayMillis')을 기록한다. (요청 처리를 마치고 다음 입력을 기다릴 때)
     * token이 null이면 리스와 상관없이 기록한다. (게임 시작)
     * token이 있는데 그 사이 다른 쪽이 리스를 가져갔으면 아무것도 하지 않고 false.
     */
    boolean schedule(String roomId, long delayMillis, String token);

    /**
     * 게임 상태 저장 + 리스 해제 + 새 마감시간('지금 + delayMillis') 기록을 한 번에 한다. (요청 처리를 마칠 때)
     * delayMillis가 음수면 마감시간 대신 그 방의 타이머(마감시간, 리스, AFK)를 지운다. (게임 종료)
     * token이 null이면 리스와 상관없이 기록한다. (게임 시작)
     * token이 있는데 그 사이 다른 쪽이 리스를 가져갔으면 상태도 타이머도 쓰지 않고 false.
     * (처리가 리스 시간보다 오래 걸려 다른 서버가 대신 진행을 시작한 경우, 그쪽의 결과를 덮어쓰지 않는다)
     */
    boolean saveAndSchedule(GameState state, long delayMillis, String token);

    /** 요청이 실패했을 때 잡았던 리스를 풀고 마감시간을 previousDeadline으로 되돌린다. 리스 주인이 바뀌었으면 아무것도 하지 않는다. */
    void restore(String roomId, String token, long previousDeadline);

    /** 마감시간이 지난 방 번호를 마감시간이 이른 순으로 최대 limit개 */
    List<String> findExpired(int limit);

    /** 방의 마감시간, 리스, AFK 목록을 모두 지운다. (게임 종료) */
    void remove(String roomId);

    void addAfk(String roomId, long playerId);

    void removeAfk(String roomId, long playerId);

    boolean isAfk(String roomId, long playerId);
}
