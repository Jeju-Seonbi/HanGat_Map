package com.example.hangat.user.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 인증 관련 Dto 모음 - 가입 / 로그인 / 비밀번호 찾기
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthDto {

        /** 로그인 요청 값*/
        public record LoginRequest(

                @NotBlank(message = "이메일을 입력해주세요.")
                @Email(message = "이메일 형식을 확인해주세요.")
                @Size(max = 255)
                String email,

                @NotBlank(message = "비밀번호를 입력해주세요.")
                @Size(max = 64)
                String password
        ) {
        }

        /** 회원 가입 */
        public record SignupRequest(

                @NotBlank(message = "이메일을 입력해주세요.")
                @Email(message = "이메일 형식을 확인해주세요.")
                @Size(max = 255)
                String email,

                @NotBlank(message = "비밀번호를 입력해주세요.")
                @Size(min = 12, max = 64, message = "비밀번호는 12자 이상이어야 합니다.")
                String password,

                @NotBlank(message = "비밀번호 확인을 입력해주세요.")
                String passwordConfirm,

                @NotBlank(message = "닉네임을 입력해주세요.")
                @Size(min = 2, max = 50, message = "닉네임은 2~50자여야 합니다.")
                String nickname
        ) {
        }

        // ────────────────────────── 비밀번호 찾기 총 3단계 ──────────────────────────

        /**
         * 1단계 - 코드 발송 요청
         * 이메일을 통해서 코드를 받게 한다.
         */
        public record SendResetCodeRequest(

                @NotBlank(message = "이메일을 입력해주세요.")
                @Email(message = "이메일 형식을 확인해주세요.")
                @Size(max = 255)
                String email
        ){
        }
        /**
         * 1단계 응답
         * 계정 존재 여부를 추측할 수 없도록 항상 같은 형식으로 응답한다.
         */
        public record SendResetCodeResponse(
                String requestId,
                long expiresIn,
                int maxAttempts
        ){
        }

        /**
         * 2단계 - 코드 확인
         * requestId가 있어야 남이 요청한 코드에서 무차별 대입하는걸 막음
         */
        public record VerifyResetCodeRequest(
                @NotBlank(message = "인증 코드를 입력해주세요.")
                @Size(max = 16, message = "인증 코드 형식을 확인해주세요.")
                String code,

                @NotBlank(message = "요청 식별자가 필요합니다.")
                @Size(min = 32, max = 32, message = "요청 식별자 형식을 확인해주세요.")
                String requestId
        ){
        }
        public record ResendVerificationRequest(
                @NotBlank @Email @Size(max = 255) String email
        ) {
        }
        /**
         * 2단계 응답
         * 마스킹된 이메일은 여기서 처음 보여준다. <- 이미 해당 이메일 갖고있음 + 코드 알고있음
         * 인 상태이기때문에 보여준다.
         */
        public record VerifyResetCodeResponse(
                String ticket,
                String maskedEmail,
                long expiresIn
        ){
        }

        /**
         * 3단계 - 새 비밀번호 설정
         * ticket은 2단계에서 받은 일회용 값.
         */
        public record ResetPasswordRequest(
                @NotBlank
                String ticket,

                @NotBlank(message = "비밀번호를 입력해주세요.")
                @Size(min = 12, max = 64, message = "비밀번호는 12자 이상이어야합니다.")
                String password,

                @NotBlank(message = "비밀번호 확인을 입력해주세요.")
                String passwordConfirm
        ){
        }
}
