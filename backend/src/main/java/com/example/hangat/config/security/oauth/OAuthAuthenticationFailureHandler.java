package com.example.hangat.config.security.oauth;

import com.example.hangat.config.security.cookie.AuthCookieManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 공급자 인증이 실패하거나 사용자가 동의를 취소했을 때
 * 임시 세션과 OAuth 진행 쿠키를 정리하고 프론트 실패 화면으로 이동시킨다.
 */
@Slf4j
@Component
public class OAuthAuthenticationFailureHandler
        implements AuthenticationFailureHandler {

    private final AuthCookieManager cookieManager;
    private final String frontendUrl;

    public OAuthAuthenticationFailureHandler(
            AuthCookieManager cookieManager,
            @Value("${app.frontend-url}") String frontendUrl) {

        this.cookieManager = cookieManager;
        this.frontendUrl = removeTrailingSlash(frontendUrl);
    }

    // ────────────────────────── 실패 상태 정리 ──────────────────────────

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        // 공급자가 돌려준 상세 메시지나 authorization code는 로그에 남기지 않는다.
        log.warn(
                "OAuth 공급자 인증 실패 type={}",
                exception.getClass().getSimpleName()
        );

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();
        cookieManager.clearOAuthFlowCookie(response);
        response.sendRedirect(frontendUrl + "/oauth/callback?result=failure");
    }

    // ────────────────────────── 프론트 URL 정규화 ──────────────────────────

    private static String removeTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
