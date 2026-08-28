package com.example.hangat.user.controller;


import com.example.hangat.common.model.BaseResponse;
import com.example.hangat.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.example.hangat.user.model.dto.UserDto.*;

/**
 * 프로필 조회 / 닉네임 변경쪽
 * 닉네임 중복확인만 가입 화면에서도 써야하니 비회원도 열어둠.
 * (회원 전용)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
@Tag(name = "User", description = "회원 프로필 API")
@Validated
public class UserController {

    private final UserService userService;

    @GetMapping("/check-nickname")
    @Operation(summary = "닉네임 중복확인", description = "가입 화면에서 쓰므로 비회원도 호출한다")
    public BaseResponse<NicknameAvailableResponse> checkNickname(
            @RequestParam @NotBlank @Size(min = 2, max = 50) String nickname) {
        return BaseResponse.success(userService.isNicknameAvailable(nickname));
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "이메일 / 닉네임 / 생년월일")
    public BaseResponse<UserResponse> me(@AuthenticationPrincipal Long userId) {
        return BaseResponse.success(userService.getProfile(userId));
    }

    @PatchMapping("/me/nickname")
    @Operation(summary = "닉네임 변경")
    public BaseResponse<UserResponse> changeNickname(@AuthenticationPrincipal Long userId,
                                                     @Valid @RequestBody NicknameRequest request) {
        return BaseResponse.success(userService.updateNickname(userId, request.nickname()));
    }
}