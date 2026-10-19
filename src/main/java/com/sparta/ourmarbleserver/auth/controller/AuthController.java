package com.sparta.ourmarbleserver.auth.controller;

import com.sparta.ourmarbleserver.auth.dto.AccessTokenResponse;
import com.sparta.ourmarbleserver.auth.dto.EmailSendRequest;
import com.sparta.ourmarbleserver.auth.dto.EmailVerifyRequest;
import com.sparta.ourmarbleserver.auth.dto.LoginRequest;
import com.sparta.ourmarbleserver.auth.dto.PlayerResponse;
import com.sparta.ourmarbleserver.auth.dto.SignupRequest;
import com.sparta.ourmarbleserver.auth.service.EmailVerificationService;
import com.sparta.ourmarbleserver.auth.service.JwtAuthService;
import com.sparta.ourmarbleserver.auth.service.PlayerService;
import com.sparta.ourmarbleserver.auth.service.SignupService;
import com.sparta.ourmarbleserver.global.exception.AuthExceptionHandler.AuthExceptionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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
    private final SignupService signupService;
    private final PlayerService playerService;
    private final EmailVerificationService emailVerificationService;

    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 닉네임으로 가입한다. 로그인 없이 호출할 수 있다.")
    @ApiResponse(responseCode = "201", description = "가입 성공. 만들어진 회원 정보(id, email, nickname)를 돌려준다. 비밀번호는 돌려주지 않는다. id는 게임 메시지의 playerId와 같은 값이다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PlayerResponse.class),
                    examples = @ExampleObject(name = "가입 성공", value = "{\"id\": 1, \"email\": \"player@example.com\", \"nickname\": \"Alice\"}")))
    @ApiResponse(responseCode = "400", description = "이메일 형식이 아니거나, 비어 있는 값이 있거나, 닉네임이 20자를 넘거나, 이메일 인증을 하지 않음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthExceptionResponse.class),
                    examples = @ExampleObject(name = "이메일 인증을 하지 않음", value = "{\"message\": \"이메일 인증이 필요합니다.\"}")))
    @PostMapping("/signup")
    public ResponseEntity<PlayerResponse> signup(
            @Valid @RequestBody SignupRequest body
    ) {
        //이메일 인증을 포함한 회원가입 이메일 인증 없이 진행하고 싶으면 이 코드를 주석치고 아래 주석을 해제
        PlayerResponse player = signupService.signup(body);

        //이메일 인증 미포함 회원가입
        //PlayerResponse player = playerService.signup(body);

        return ResponseEntity.status(HttpStatus.CREATED).body(player);
    }

    @Operation(summary = "이메일 인증번호 발송", description = "입력한 이메일로 6자리 인증번호를 발송한다. 인증번호는 5분 동안 유효하다. 로그인 없이 호출할 수 있다.")
    @ApiResponse(responseCode = "204", description = "인증번호 발송 성공 (본문 없음)")
    @ApiResponse(responseCode = "400", description = "이메일 형식이 아니거나 이메일이 비어 있음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthExceptionResponse.class),
                    examples = {
                            @ExampleObject(name = "이메일 형식이 틀림", value = "{\"message\": \"이메일 형식이 올바르지 않습니다.\"}"),
                            @ExampleObject(name = "이메일이 비어 있음", value = "{\"message\": \"이메일을 입력해 주세요.\"}")}))
    @ApiResponse(responseCode = "409", description = "이미 가입된 이메일",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthExceptionResponse.class),
                    examples = @ExampleObject(name = "이미 가입된 이메일", value = "{\"message\": \"이미 가입된 이메일입니다.\"}")))
    @ApiResponse(responseCode = "502", description = "메일 서버를 통한 발송 실패",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthExceptionResponse.class),
                    examples = @ExampleObject(name = "메일 발송 실패", value = "{\"message\": \"인증 메일 발송에 실패했습니다.\"}")))
    @PostMapping("/email/send")
    public ResponseEntity<Void> emailSend(
            @Valid @RequestBody EmailSendRequest request
    ) {
        emailVerificationService.sendCode(request.getEmail());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "이메일 인증번호 확인", description = "이메일로 받은 6자리 인증번호를 확인한다. 성공하면 해당 이메일은 30분 동안 회원가입할 수 있다. 로그인 없이 호출할 수 있다.")
    @ApiResponse(responseCode = "204", description = "이메일 인증 성공 (본문 없음)")
    @ApiResponse(responseCode = "400", description = "인증번호가 틀렸거나, 없거나, 만료됐거나, 6자리 숫자가 아님",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthExceptionResponse.class),
                    examples = {
                            @ExampleObject(name = "인증번호가 없거나 만료됨", value = "{\"message\": \"인증번호가 없거나 만료되었습니다.\"}"),
                            @ExampleObject(name = "인증번호가 틀림", value = "{\"message\": \"인증번호가 일치하지 않습니다.\"}"),
                            @ExampleObject(name = "6자리 숫자가 아님", value = "{\"message\": \"인증번호는 6자리 숫자여야 합니다.\"}")}))
    @PostMapping("/email/verify")
    public ResponseEntity<Void> emailVerify(
            @Valid @RequestBody EmailVerifyRequest request
    ) {
        emailVerificationService.verifyCode(request.getEmail(), request.getCode());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "로그인", description = "이메일과 비밀번호가 맞으면 accessToken(JWT)을 돌려준다. 로그인 없이 호출할 수 있다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공. accessToken(JWT)을 돌려준다. 30분 뒤 만료되고, 이후 요청의 Authorization 헤더에 \"Bearer {accessToken}\"으로 보낸다. 웹소켓 접속에도 같은 토큰을 쓴다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AccessTokenResponse.class),
                    examples = @ExampleObject(name = "로그인 성공", value = "{\"accessToken\": \"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature\"}")))
    @ApiResponse(responseCode = "401", description = "없는 이메일이거나 비밀번호가 틀림",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthExceptionResponse.class),
                    examples = @ExampleObject(name = "이메일 또는 비밀번호가 틀림", value = "{\"message\": \"이메일 또는 비밀번호가 올바르지 않습니다.\"}")))
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
    @ApiResponse(responseCode = "401", description = "토큰이 없거나, 틀렸거나, 만료됨 (본문 없음)")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}