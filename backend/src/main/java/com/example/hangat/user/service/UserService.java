package com.example.hangat.user.service;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.common.util.EmailNormalizer;
import com.example.hangat.config.security.password.PasswordHasher;
import com.example.hangat.user.model.User;
import com.example.hangat.user.model.dto.AuthDto;
import com.example.hangat.user.model.dto.UserDto;
import com.example.hangat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final EmailVerificationService emailVerificationService;

    // 회원가입
    @Transactional
    public UserDto.UserResponse signup(AuthDto.SignupRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        String nickname = request.nickname().trim();

        // 이메일이 이미 존재하는지 or 닉네임이 중복인지 확인.
        if(userRepository.existsByEmail(email)) {
            throw new BaseException(BaseResponseStatus.DUPLICATE_EMAIL);
        }else if(userRepository.existsByNickname(nickname)) {
            // 가입 버튼 더블 클릭같은 같은 요청을 막음. 물론 프론트도 막지만 혹시 모르니
            throw new BaseException(BaseResponseStatus.DUPLICATE_NICKNAME);
        }

        // 중복검사하고 Hash로 바꾸기.
        String encoded = passwordHasher.encodeNew(
                request.password(), request.passwordConfirm());

        User user = userRepository.save(
                User.signUpWithEmail(email, encoded, nickname, request.birthDate())
        );

        emailVerificationService.issue(user);
        return UserDto.UserResponse.form(user);
    }

    // 내 정보 조회
    public UserDto.UserResponse getProfile(Long userId) {
        return UserDto.UserResponse.form(getUserOrThrow(userId));
    }

    // 닉네임 변경
    @Transactional
    public UserDto.UserResponse updateNickname(Long userId, String newNickname) {
        String nickname = newNickname.trim();
        User user = getUserOrThrow(userId);

        // 자기 닉네임은 그대로 저장하는걸 허용.
        if (!nickname.equals(user.getNickname())
                && userRepository.existsByNickname(nickname)) {
            throw new BaseException(BaseResponseStatus.DUPLICATE_NICKNAME);
        }
        user.setNickname(nickname);
        return UserDto.UserResponse.form(user);
    }

    // 닉네임 중복 확인.
    public UserDto.NicknameAvailableResponse isNicknameAvailable(String nickname) {
        String checkNickname = (nickname == null) ? "" : nickname.trim();
        return new
                UserDto.NicknameAvailableResponse(
                        !userRepository.existsByNickname(checkNickname));
    }

    private User getUserOrThrow(Long userId) {
        if (userId == null) {
            throw new BaseException(BaseResponseStatus.JWT_INVALID);
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.USER_NOT_FOUND));
    }
}
