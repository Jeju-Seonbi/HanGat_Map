package com.example.hangat.user.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 회원가입 요청 (USER_002).
 * 여기선 형식만 봄. 중복·비번확인 일치·bcrypt 72바이트는 서비스에서 처리.
 */
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
        String nickname,

        @Past(message = "생년월일을 확인해주세요.")
        LocalDate birthDate
) {
}
