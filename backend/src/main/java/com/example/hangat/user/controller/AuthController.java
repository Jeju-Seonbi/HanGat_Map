package com.example.hangat.user.controller;

import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.user.model.dto.AuthDto;
import com.example.hangat.user.model.dto.TokenDto;
import com.example.hangat.user.model.dto.UserDto;
import com.example.hangat.user.service.AuthService;
import com.example.hangat.user.service.EmailVerificationService;
import com.example.hangat.user.service.PasswordResetService;
import com.example.hangat.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * 가입 / 로그인 API - 비회원도 부를 수 있는 경로다.
 * 가입 / 로그인 / 로그아웃 / 쿠키 / 인증등은 이쪽 위주로 한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@Tag(name = "Auth", description = "가입 / 로그인 API")
@Validated
public class AuthController {

    private static final String REFRESH_COOKIE = "hangat_rt";

    private final UserService userService;
    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    /** 로컬 http에서는 false로 됌. Secure 쿠키는 https에서만 저장돼서 개발 중에 조용히 안 실림 */
    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    // ────────────────────────── 가입 및 로그인 ──────────────────────────
    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "가입 후 인증 메일을 보낸다.")
    public BaseResponse<UserDto.UserResponse> signup(@Valid @RequestBody AuthDto.SignupRequest request) {
        return BaseResponse.success(userService.signup(request));
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "로그인 합니다잉")
    public BaseResponse<TokenDto.LoginResponse> login(@Valid @RequestBody AuthDto.LoginRequest request,
                                                      HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request);
        setRefreshCookie(response, result.rawRefreshToken());
        return BaseResponse.success(result.body());
    }

    @PostMapping("/reissue")
    @Operation(summary = "access 재발급", description = "쿠키의 refresh를 교체함.")
    public BaseResponse<TokenDto.AccessTokenResponse> reissue(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        AuthService.ReissueResult result = authService.reissue(refreshToken);
        setRefreshCookie(response, result.rawRefreshToken());
        return BaseResponse.success(result.body());
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "서버 토큰을 폐기하고 쿠키를 지운다")
    public BaseResponse<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);
        clearRefreshCookie(response);
        return BaseResponse.success(null);
    }

    // ────────────────────────── 이메일 인증 ──────────────────────────
    @GetMapping("/verify")
    @Operation(summary = "이메일 인증", description = "이메일로 보낸 링크를 통해서 인증함.")
    public BaseResponse<Void> verifyEmail(@RequestParam @NotBlank String token) {
        emailVerificationService.verify(token);
        return BaseResponse.success(null);
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "인증 메일 재발송", description = "한번 더 보낸다잇. 추가로 기존 토큰 무효화.")
    public BaseResponse<Void> resendVerification(
            @Valid @RequestBody AuthDto.ResendVerificationRequest request) {
        emailVerificationService.resend(request);
        return BaseResponse.success(null);
    }

    // ────────────────────────── 비밀번호 찾기 3단계 ──────────────────────────
    @PostMapping("/password/code")
    @Operation(summary = "1단계 - 코드 발송", description = "계정이 없어도 같은 응답이 나간다")
    public BaseResponse<AuthDto.SendResetCodeResponse> sendResetCode(
            @Valid @RequestBody AuthDto.SendResetCodeRequest request) {
        return BaseResponse.success(passwordResetService.sendCode(request));
    }

    @PostMapping("/password/verify")
    @Operation(summary = "2단계 - 코드 확인", description = "통과하면 일회용 티켓을 준다")
    public BaseResponse<AuthDto.VerifyResetCodeResponse> verifyResetCode(
            @Valid @RequestBody AuthDto.VerifyResetCodeRequest request) {
        return BaseResponse.success(passwordResetService.verifyCode(request));
    }

    @PostMapping("/password/reset")
    @Operation(summary = "3단계 - 새 비밀번호 설정", description = "성공하면 모든 기기 세션이 끊긴다")
    public BaseResponse<Void> resetPassword(@Valid @RequestBody AuthDto.ResetPasswordRequest request,
                                            HttpServletResponse response) {
        passwordResetService.resetPassword(request);
        clearRefreshCookie(response);
        return BaseResponse.success(null);
    }

    // ────────────────────────── 쿠키 ──────────────────────────

    /**
     * refresh는 body로 안 주고 HttpOnly 쿠키로만 준다.
     * path를 /auth로 좁혀서 refresh는 일반 API를 요청할 떄 관여를 못하게 함.
     */
    private void setRefreshCookie(HttpServletResponse response, String rawToken) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildRefreshCookie(rawToken, Duration.ofDays(14)).toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildRefreshCookie("", Duration.ZERO).toString());
    }

    /** SameSite=None은 Secure가 필수라 둘을 같이 움직인다 */
    private ResponseCookie buildRefreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSecure ? "None" : "Lax")
                .path("/auth")
                .maxAge(maxAge)
                .build();
    }
}
