package com.sparta.ourmarbleserver.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayRecord {

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(nullable = false)
    private int gamesPlayed;

    @Column(nullable = false)
    private int gamesWon;

    public PlayRecord(String nickname) {
        this.nickname = nickname;
    }

    public void recordGame(boolean won) {
        gamesPlayed++;
        if (won) {
            gamesWon++;
        }
    }

    public void reset() {
        gamesPlayed = 0;
        gamesWon = 0;
    }
}
