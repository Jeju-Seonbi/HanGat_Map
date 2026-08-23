package com.example.hangat.user.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청 (USER_001).
 * 비번 복잡도는 다시 안 봄 - 옛날 정책으로 만든 비번 가진 회원이 로그인 자체를 못 하게 됨.
 * 상한 64자는 긴 문자열로 BCrypt 돌리게 만드는 DoS 차단용.
 */
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
