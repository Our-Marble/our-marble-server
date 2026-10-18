package com.sparta.ourmarbleserver.config;

import com.sparta.ourmarbleserver.auth.dto.AuthPlayer;
import com.sparta.ourmarbleserver.auth.entity.Player;
import com.sparta.ourmarbleserver.auth.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class JwtProvider {
    private final SecretKey secretKey;
    private final long accessTtlSeconds;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-ttl-seconds}") long accessTtlSeconds
    ) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String createAccessToken(
            Player player
    ) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTtlSeconds);
        // 회원 정보와 만료 시각을 담아 JWT 발급
        return Jwts.builder()
                .subject(player.getId().toString())
                .claim("role", player.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public AuthPlayer parseToken(
            String token
    ) {
        try {
            // JWT 서명과 만료 검증
            Jws<Claims> signedClaims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            Claims claims = signedClaims.getPayload();
            String subject = claims.getSubject();
            long userId = Long.parseLong(subject);
            Role role = Role.valueOf(claims.get("role", String.class));
            // 검증한 회원 정보로 인증 객체 생성
            return new AuthPlayer(userId, role);
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
