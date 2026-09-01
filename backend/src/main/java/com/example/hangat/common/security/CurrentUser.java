package com.example.hangat.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 현재 요청의 회원 id - <b>비로그인도 허용되는 API</b>에서 쓴다.
 *
 * <p>인증 필수 API는 컨트롤러 파라미터로 {@code Authentication}을 받아 principal을 그냥 캐스팅하면 되지만,
 * 코스 조회·스왑처럼 비로그인이 열려 있는 경로에서는 익명 토큰({@code AnonymousAuthenticationToken})이
 * 들어와 principal이 Long이 아니다. 그 분기를 한 곳에 모아 둔다.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** 로그인 회원이면 id, 비로그인·익명이면 null. */
    public static Long idOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof Long userId ? userId : null;
    }
}
