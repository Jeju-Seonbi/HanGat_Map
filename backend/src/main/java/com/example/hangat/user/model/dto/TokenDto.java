package com.example.hangat.user.model.dto;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 토큰 응답 DTO 모음
 * refresh 토큰은 여기 절대 안 단김 - HttpOnly 쿠키로만 나감.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenDto {

    /**
     * access 토큰
     */
    public record AccessTokenResponse(
            String accessToken,
            long expiresIn
    ){
    }

    /**
     * 로그인 응답
     */
    public record LoginResponse(
            UserDto.UserResponse user,
            AccessTokenResponse tokens
    ){
    }
}
