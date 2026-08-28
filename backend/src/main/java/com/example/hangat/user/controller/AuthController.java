package com.example.hangat.user.controller;

import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.config.security.AuthRequestLimiter;
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
import jakarta.servlet.http.HttpServletRequest;
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
    private final AuthRequestLimiter requestLimiter;

    /** 로컬 HTTP 환경에서는 Secure 쿠키를 사용할 수 없으므로 false로 설정한다. */
    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    // ────────────────────────── 가입 및 로그인 ──────────────────────────
    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "가입 후 인증 메일을 보낸다.")
    public BaseResponse<UserDto.UserResponse> signup(@Valid @RequestBody AuthDto.SignupRequest request) {
        return BaseResponse.success(userService.signup(request));
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인한다.")
    public BaseResponse<TokenDto.LoginResponse> login(@Valid @RequestBody AuthDto.LoginRequest request,
                                                       HttpServletRequest httpRequest,
                                                       HttpServletResponse response) {
        requestLimiter.checkLogin(clientIp(httpRequest), request.email());
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
    @Operation(summary = "인증 메일 재발송", description = "기존 인증 토큰을 무효화하고 새 메일을 발송한다.")
    public BaseResponse<Void> resendVerification(
            @Valid @RequestBody AuthDto.ResendVerificationRequest request,
            HttpServletRequest httpRequest) {
        requestLimiter.checkEmailRequest(
                "resend-verification", clientIp(httpRequest), request.email());
        emailVerificationService.resend(request);
        return BaseResponse.success(null);
    }

    // ────────────────────────── 비밀번호 찾기 3단계 ──────────────────────────
    @PostMapping("/password/code")
    @Operation(summary = "1단계 - 코드 발송", description = "계정이 없어도 같은 응답이 나간다")
    public BaseResponse<AuthDto.SendResetCodeResponse> sendResetCode(
            @Valid @RequestBody AuthDto.SendResetCodeRequest request,
            HttpServletRequest httpRequest) {
        requestLimiter.checkEmailRequest(
                "password-reset", clientIp(httpRequest), request.email());
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
     * 쿠키 경로를 /auth로 제한해 일반 API 요청에는 포함되지 않도록 한다.
     */
    private void setRefreshCookie(HttpServletResponse response, String rawToken) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildRefreshCookie(rawToken, Duration.ofDays(14)).toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildRefreshCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie buildRefreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/auth")
                .maxAge(maxAge)
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        String cloudflareIp = request.getHeader("CF-Connecting-IP");
        if (cloudflareIp != null && !cloudflareIp.isBlank()) {
            return cloudflareIp.trim();
        }
        return request.getRemoteAddr();
    }
}
