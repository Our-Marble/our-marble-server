package com.sparta.ourmarbleserver.global.protocol;

/**
 * 메시지의 "topic" 필드 값. 어떤 종류의 메시지인지(로비용/게임용) 나타내며,
 * 서버는 이 값으로 요청을 어느 처리기로 보낼지 고른다.
 * 누구에게 보낼지(구독 주소)는 {@link TopicPath}가 따로 맡는다.
 */
public enum MessageTopic {
    LOBBY, // 로비(방 목록)와 대기방
    GAME   // 게임 진행
}
