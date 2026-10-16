package com.sparta.ourmarbleserver.global.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 이 서버 프로세스를 구분하는 id. 서버를 켤 때마다 새로 만든다.
 * presence 값("서버id:세션id")에 쓴다.
 */
@Slf4j
@Component
public class ServerInstance {
    private final String id;

    public ServerInstance() {
        this(UUID.randomUUID().toString().substring(0, 8));
    }

    ServerInstance(String id) {
        this.id = id;
        log.info("[서버] id={}", id);
    }

    public String id() {
        return id;
    }
}
