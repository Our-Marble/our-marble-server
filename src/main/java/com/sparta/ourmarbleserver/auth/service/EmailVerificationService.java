package com.sparta.ourmarbleserver.auth.service;

import com.sparta.ourmarbleserver.auth.repository.EmailVerificationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final int CODE_BOUND = 1_000_000;

    private final EmailVerificationStore emailVerificationStore;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.mail.from}")
    private String from;

    public void sendCode(String email) {
        String code = "%06d".formatted(secureRandom.nextInt(CODE_BOUND));
        emailVerificationStore.saveCode(email, code);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("[Our Marble] 이메일 인증번호");
        message.setText("이메일 인증번호는 " + code + "입니다. 5분 안에 입력해 주세요.");

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            emailVerificationStore.deleteCode(email);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "인증 메일 발송에 실패했습니다.",
                    exception
            );
        }
    }

    public void verifyCode(String email, String submittedCode) {
        String savedCode = emailVerificationStore.findCode(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "인증번호가 없거나 만료되었습니다."
                ));

        if (!savedCode.equals(submittedCode)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "인증번호가 일치하지 않습니다."
            );
        }

        emailVerificationStore.deleteCode(email);
        emailVerificationStore.markVerified(email);
    }

    public void requireVerified(String email) {
        if (!emailVerificationStore.isVerified(email)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이메일 인증이 필요합니다."
            );
        }
    }

    public void deleteVerification(String email) {
        emailVerificationStore.deleteVerified(email);
    }
}
