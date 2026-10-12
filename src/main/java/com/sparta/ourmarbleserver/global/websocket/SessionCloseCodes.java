package com.sparta.ourmarbleserver.global.websocket;

import org.springframework.web.socket.CloseStatus;

/**
 * 서버가 연결을 끊을 때 보내는 이유 번호. 4000~4999는 앱이 자유롭게 정할 수 있는 범위다.
 * 클라는 번호로 판단하고, 문구는 로그 확인용이다. (문구는 123바이트 제한이라 짧은 영문)
 */
public final class SessionCloseCodes {
    public static final CloseStatus DUPLICATE_LOGIN = new CloseStatus(4001, "DUPLICATE_LOGIN");
    private SessionCloseCodes() {}   // 객체 생성 막기
}