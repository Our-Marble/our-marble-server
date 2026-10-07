package com.sparta.ourmarbleserver.auth.controller;

import com.sparta.ourmarbleserver.auth.consts.SessionConst;
import com.sparta.ourmarbleserver.auth.dto.LoginRequest;
import com.sparta.ourmarbleserver.auth.dto.MemberResponse;
import com.sparta.ourmarbleserver.auth.dto.SignupRequest;
import com.sparta.ourmarbleserver.auth.service.AuthService;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;


@RestController
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<MemberResponse> signup(
            @RequestBody SignupRequest body
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(body));
    }

    @PostMapping("/login")
    public ResponseEntity<MemberResponse> login(
            @RequestBody LoginRequest body,
            HttpServletRequest request
    ) {
        MemberResponse member = authService.authenticate(body);
        HttpSession session = request.getSession(); //세션 생성(기본값이 true)
        request.changeSessionId();
        session.setAttribute(SessionConst.LOGIN_MEMBER_ID, member.getId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(member);
    }

    @GetMapping("/me")
    public ResponseEntity<MemberResponse> me(
            @SessionAttribute(name = SessionConst.LOGIN_MEMBER_ID, required = false) Long memberId
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authService.requireMember(memberId));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request
    ) {
        HttpSession session = request.getSession(false); //세션 삭제
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }
}