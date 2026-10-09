package com.sparta.ourmarbleserver.auth.controller;

import com.sparta.ourmarbleserver.auth.dto.AccessTokenResponse;
import com.sparta.ourmarbleserver.auth.dto.LoginRequest;
import com.sparta.ourmarbleserver.auth.dto.PlayerResponse;
import com.sparta.ourmarbleserver.auth.dto.SignupRequest;
import com.sparta.ourmarbleserver.auth.service.JwtAuthService;
import com.sparta.ourmarbleserver.auth.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "로그인", description = "회원가입과 로그인. 로그인하면 accessToken(JWT)을 받고, 이후 요청의 Authorization 헤더에 \"Bearer {토큰}\"으로 보낸다. 토큰 유효 시간은 30분이다.")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "POST /auth/login 으로 받은 accessToken")
@RestController()
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final JwtAuthService jwtAuthService;
    private final PlayerService playerService;

    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 닉네임으로 가입한다. 로그인 없이 호출할 수 있다.")
    @ApiResponse(responseCode = "201", description = "가입 성공. 만들어진 회원 정보를 돌려준다.")
    @ApiResponse(responseCode = "400", description = "이메일 형식이 아니거나, 비어 있는 값이 있거나, 닉네임이 20자를 넘음")
    @PostMapping("/signup")
    public ResponseEntity<PlayerResponse> signup(
            @Valid @RequestBody SignupRequest body
    ) {
        PlayerResponse player = playerService.signup(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(player);
    }

    @Operation(summary = "로그인", description = "이메일과 비밀번호가 맞으면 accessToken(JWT)을 돌려준다. 로그인 없이 호출할 수 있다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "401", description = "없는 이메일이거나 비밀번호가 틀림")
    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(
            @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(jwtAuthService.login(request));
    }

    //형식상 만들어 놓음 실제 기능은 없음
    @Operation(summary = "로그아웃", description = "서버는 아무것도 하지 않고 204만 돌려준다. 토큰은 서버에 저장되지 않으므로, 클라이언트가 저장한 토큰을 지우면 로그아웃이다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "204", description = "성공 (본문 없음)")
    @ApiResponse(responseCode = "401", description = "토큰이 없거나, 틀렸거나, 만료됨")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
