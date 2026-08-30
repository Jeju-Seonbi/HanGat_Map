package com.example.hangat.user.model.dto;

import com.example.hangat.config.security.token.VerificationCodeGenerator;
import com.example.hangat.user.model.oauth.AuthProvider;
import com.example.hangat.user.model.oauth.OAuthFlowStep;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * OAuth 요청, 응답 DTO 모음
 *
 * provider의 UID나 사용자 ID, 이메일은 요청 본문에서 받지 않는다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OAuthDto {

    // ────────────────────────── 진행 상태 조회 ──────────────────────────

    /**
     *  현재 OAuth 가입, 연결 단계 응답.
     *  기존 계정의 원본 이메일은 반환하지 않고 maskedExistingEmail만 반환.
     */
    public record FlowResponse(
            AuthProvider provider,
            OAuthFlowStep nextStep,
            String providerEmail,
            String maskedExistingEmail
    ) {
    }

    // ────────────────────────── 인증 코드 발송 ──────────────────────────

    /**
     * 신규 소셜 가입용 이메일과 닉네임.
     * Google은 이메일을 보내지 않고 닉네임만 보내며 Kakao는 둘 다 보낸다.
     */
    public record SendSignupCodeRequest(
            @Email(message = "이메일 형식을 확인해주세요.")
            @Size(max = 255)
            String email,

            @NotBlank(message = "닉네임을 입력해주세요.")
            @Size(
                    min = 2,
                    max = 50,
                    message = "닉네임은 2~50자 이내로 하셔야합니다."
            )
            String nickname
    ){
    }

    // 이메일 코드 발송된 결과.
    public record SendCodeResponse(
            OAuthFlowStep nextStep,
            String maskedExistingEmail,
            long expiresIn,
            int maxAttempts
    ){
    }

    // ────────────────────────── 인증 코드 검증 ──────────────────────────

    // OAuth 이메일 6자리 코드 검증 요청.
    public record VerifyCodeRequest(
            @NotBlank(message = "인증 코드를 입력해주세요.")
            @Pattern(
                    regexp = VerificationCodeGenerator.INPUT_PATTERN,
                    message = "인증 코드는 영문자와 숫자 6자리여야 합니다."
            )
            String code
    ){
    }

    /**
     *  코드 검증 결과.
     *
     *  가입 또는 연결이 되면 login에 accessToken을 포함하고
     *  Kakao 기존 계정 확인이 필요하면 login은 null이다.
     */
    public record VerifyCodeResponse(
            OAuthFlowStep nextStep,
            String maskedExistingEmail,
            TokenDto.LoginResponse login
    ){
    }

}
