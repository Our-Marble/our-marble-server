package com.sparta.ourmarbleserver.global.websocket;

import java.util.Map;

/**
 * 누가 접속 중인지 서버 사이에서 공유하는 저장소.
 * 값(token)은 "서버id:세션id"로, 어느 서버의 어느 연결이 남긴 기록인지 구분한다.
 * 서버는 RedisPresenceRepository, 테스트는 InMemoryPresenceRepository를 쓴다.
 */
public interface PresenceRepository {

    // 접속 기록, 새 연결 들어오면 덮어쓰고 이전 token을 돌려준다 (없었으면 null)
    String markOnline(long playerId, String token);

    // 기록과 토큰이 일치하면 삭제 (새 연결 삭제 방지)
    void markOffline(long playerId, String token);

    // 기록과 토큰이 일치하면 만료 시간 연장
    void refresh(Map<Long, String> tokens);

    // 어느 서버에든 접속 중인가
    boolean isOnline(long playerId);
}
