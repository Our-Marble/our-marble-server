package com.sparta.ourmarbleserver.auth.repository;

import com.sparta.ourmarbleserver.auth.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    Optional<Player> findByEmail(String email);
}