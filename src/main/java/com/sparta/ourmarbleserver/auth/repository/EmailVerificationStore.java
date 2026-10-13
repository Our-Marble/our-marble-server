package com.sparta.ourmarbleserver.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EmailVerificationStore {

    private static final String CODE_PREFIX = "auth:email:code:";
    private static final String VERIFIED_PREFIX = "auth:email:verified:";

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    public void saveCode(String email, String code) {
        redisTemplate.opsForValue()
                .set(codeKey(email), code, CODE_TTL);
    }

    public Optional<String> findCode(String email) {
        String code = redisTemplate.opsForValue()
                .get(codeKey(email));

        return Optional.ofNullable(code);
    }

    public void deleteCode(String email) {
        redisTemplate.delete(codeKey(email));
    }

    public void markVerified(String email) {
        redisTemplate.opsForValue()
                .set(verifiedKey(email), "true", VERIFIED_TTL);
    }

    public boolean isVerified(String email) {
        String value = redisTemplate.opsForValue()
                .get(verifiedKey(email));

        return "true".equals(value);
    }

    public void deleteVerified(String email) {
        redisTemplate.delete(verifiedKey(email));
    }

    private String codeKey(String email) {
        return CODE_PREFIX + email;
    }

    private String verifiedKey(String email) {
        return VERIFIED_PREFIX + email;
    }
}
