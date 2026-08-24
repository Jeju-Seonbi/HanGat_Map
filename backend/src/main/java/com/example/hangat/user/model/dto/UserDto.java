package com.example.hangat.user.model.dto;

import com.example.hangat.user.model.User;
import com.example.hangat.user.model.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 회원 정보 DTO 모음
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserDto {

    /** 닉네임 변경 */
    public record NicknameRequest(
            @NotBlank(message = "닉네임을 입력해주세요.")
            @Size(min = 2, max = 50, message = "닉네임은 2~50자 이내로 하셔야합니다.")
            String nickname
    ){
    }
    public record NicknameAvailableResponse(boolean available) {
    }

    /**
     * 회원 정보 응답
     */
    public record UserResponse(
            Long userId,
            String  email,
            String nickname,
            LocalDate birthDate,
            UserStatus status,
            boolean emailVerified,
            LocalDateTime lastLoginAt,
            LocalDateTime createdAt
    ) {

        public static UserResponse form(User user) {
            return new UserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getNickname(),
                    user.getBirthDate(),
                    user.getStatus(),
                    user.isEmailVerified(),
                    user.getLastLoginAt(),
                    user.getCreatedAt()
            );
        }
    }
}
