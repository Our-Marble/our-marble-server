package com.sparta.ourmarbleserver.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "players")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Email
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Embedded
    private PlayRecord playRecord;

    /** 고른 초상화 번호(0~7). 고른 적이 없으면 null. */
    @Column
    private Integer portraitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    public Player(String email, String password, String nickname) {
        this.email = email;
        this.password = password;
        this.playRecord = new PlayRecord(nickname);
        this.role = Role.USER;
    }

    public void changePortrait(int portraitId) {
        this.portraitId = portraitId;
    }
}
