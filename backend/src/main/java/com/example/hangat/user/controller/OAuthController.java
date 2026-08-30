package com.example.hangat.user.controller;

import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.config.security.cookie.AuthCookieManager;
import com.example.hangat.user.model.dto.AuthInternalDto;
import com.example.hangat.user.model.dto.OAuthDto;
import com.example.hangat.user.model.dto.TokenDto;
import com.example.hangat.user.service.OAuthOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * OAuth 소셜 전용 컨트롤러.
 * provider UID나 ID, 토큰 등 요청 본문으로 받지 않는다.
 * HttpOnly 쿠키를 사용하여 진행상황을 확인함.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/oauth")
@Tag(
        name = "OAuth",
        description = "Google이나 Kakao 소셜 가입 및 계정 연결 API"
)
public class OAuthController {

    private final OAuthOnboardingService onboardingService;
    private final AuthCookieManager cookieManager;

    // 현재 가입, 연결 단계 조회.
    @GetMapping("/flow")
    @Operation(summary = "OAuth 가입 연결 진행상태 조회.")
    public BaseResponse<OAuthDto.FlowResponse> getFlow(
            @CookieValue(
                    name = AuthCookieManager.OAUTH_FLOW_COOKIE,
                    required = false
            )
            String flowToken) {

        return BaseResponse.success(
                onboardingService.getFlow(
                        flowToken
                )
        );
    }

    // 신규 소셜 가입용 이메일 인증코드 발송.
    @PostMapping("/signup/code")
    @Operation(summary = "소셜 신규 가입 이메일 코드 발송")
    public BaseResponse<OAuthDto.SendCodeResponse> sendSignupCode(
            @CookieValue(
                    name = AuthCookieManager.OAUTH_FLOW_COOKIE,
                    required = false
            )
            String flowToken,
            @Valid
            @RequestBody
            OAuthDto.SendSignupCodeRequest request,
            HttpServletRequest httpRequest) {

        return BaseResponse.success(
                onboardingService.sendSignupCode(
                        flowToken,
                        request,
                        clientIp(httpRequest)
                )
        );
    }

    // ────────────────────────── 구글 계정 ──────────────────────────

    // 구글 기존 계정 연결용 이메일 인증코드 발송.
    @PostMapping("/link/code")
    @Operation(summary = "Google 기존 계정 연결 코드 발송")
    public BaseResponse<OAuthDto.SendCodeResponse> sendLinkCode(
            @CookieValue(
                    name = AuthCookieManager.OAUTH_FLOW_COOKIE,
                    required = false
            )
            String flowToken,
            HttpServletRequest httpRequest) {

        return BaseResponse.success(
                onboardingService.sendLinkCode(
                        flowToken,
                        clientIp(httpRequest)
                )
        );
    }

    // 이메일 코드 검증하고 가능한 경우 - 신규 가입 또는 구글에 연결한다.
    @PostMapping("/code/verify")
    @Operation(summary = "소셜 이메일 코드 확인")
    public BaseResponse<OAuthDto.VerifyCodeResponse> verifyCode(
            @CookieValue(
                    name = AuthCookieManager.OAUTH_FLOW_COOKIE,
                    required = false
            )
            String flowToken,
            @Valid
            @RequestBody
            OAuthDto.VerifyCodeRequest request,
            HttpServletResponse response) {

        AuthInternalDto.OAuthVerificationResult result =
                onboardingService.verifyCode(
                        flowToken,
                        request
                );

        if(result.isLoginCompleted()) {
            completeLoginCookies(
                    response,
                    result.loginResult()
            );
        }

        return BaseResponse.success(result.response());
    }

    /**
     * 이메일 인증 후 기존 계정 연결을 완료하고 자동 로그인 기능.
     * Kakao 기존 계정이 주 흐름이며
     * Google 가입 도중 발생한 이메일 경쟁 상황에서도 사용할 수 있음.
     */
    @PostMapping("/link/complete")
    @Operation(summary = "기존 계정 연결 완료")
    public BaseResponse<TokenDto.LoginResponse> completeLink(
            @CookieValue(
                    name = AuthCookieManager.OAUTH_FLOW_COOKIE,
                    required = false
            )
            String flowToken,
            HttpServletResponse response) {

        AuthInternalDto.LoginResult result =
                onboardingService.completeLink(
                        flowToken
                );

        completeLoginCookies(response, result);

        return BaseResponse.success(result.body());
    }

    // ────────────────────────── 소셜 가입 혹은 연동 취소 ──────────────────────────
    @DeleteMapping("/flow")
    @Operation(summary = "OAuth 가입 혹은 연결 취소")
    public BaseResponse<Void> cancel(
            @CookieValue(
                    name = AuthCookieManager.OAUTH_FLOW_COOKIE,
                    required = false
            )
            String flowToken,
            HttpServletResponse response) {

        onboardingService.cancel(flowToken);
        cookieManager.clearOAuthFlowCookie(response);

        return BaseResponse.success(null);
    }

    // 자동 로그인후 refreshToken 발급 및 OAuth 진행 쿠키 삭제.
    private void completeLoginCookies(
            HttpServletResponse response,
            AuthInternalDto.LoginResult result) {

        cookieManager.setRefreshCookie(response, result.rawRefreshToken());

        cookieManager.clearOAuthFlowCookie(response);
    }

    // ────────────────────────── IP 조회 ──────────────────────────

    private String clientIp(
            HttpServletRequest request) {

        String cloudflareIp =
                request.getHeader(
                        "CF-Connecting-IP"
                );

        if (cloudflareIp != null
                && !cloudflareIp.isBlank()) {
            return cloudflareIp.trim();
        }

        return request.getRemoteAddr();
    }
}
