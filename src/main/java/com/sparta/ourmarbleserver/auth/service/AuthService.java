package com.sparta.ourmarbleserver.auth.service;

import com.sparta.ourmarbleserver.auth.dto.LoginRequest;
import com.sparta.ourmarbleserver.auth.dto.MemberResponse;
import com.sparta.ourmarbleserver.auth.dto.SignupRequest;
import com.sparta.ourmarbleserver.auth.entity.Member;
import com.sparta.ourmarbleserver.auth.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final MemberRepository memberRepository;

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        Member member = memberRepository.save(new Member(request.getEmail(), request.getPassword()));
        return new MemberResponse(member.getId(), member.getEmail());
    }

    @Transactional(readOnly = true)
    public MemberResponse authenticate(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!member.getPassword().equals(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return new MemberResponse(member.getId(), member.getEmail());
    }

    @Transactional(readOnly = true)
    public MemberResponse requireMember(Long memberId) {
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new MemberResponse(member.getId(), member.getEmail());
    }
}