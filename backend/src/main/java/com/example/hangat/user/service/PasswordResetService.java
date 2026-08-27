package com.example.hangat.user.service;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.common.util.EmailNormalizer;
import com.example.hangat.config.mail.AuthMailSender;
import com.example.hangat.config.security.PasswordHasher;
import com.example.hangat.config.security.ResetCodeGenerator;
import com.example.hangat.config.security.TokenHasher;
import com.example.hangat.user.model.PasswordResetRequest;
import com.example.hangat.user.model.RefreshRevokeReason;
import com.example.hangat.user.model.User;
import com.example.hangat.user.model.dto.AuthDto;
import com.example.hangat.user.repository.PasswordResetRequestRepository;
import com.example.hangat.user.repository.RefreshTokenRepository;
import com.example.hangat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 *  비밀번호 찾기 - 총 3단계
 *  코드 발송 -> 코드 확인 후 티켓 발송 -> 티켓으로 새 비밀번호 설정
 *  모든 응답이 계정 유무 상관없이 똑같아야 함.
 *  이유 - 보안적으로 다를 경우 계정 열거 수단이 되어버림.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetRequestRepository resetRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshRepository;
    private final PasswordHasher passwordHasher;
    private final AuthMailSender mailSender;

    // 1단계 - 코드 발송 (똑같은 응답을 보내야 함.)
    @Transactional
    public AuthDto.SendResetCodeResponse sendCode(AuthDto.SendResetCodeRequest request) {
        String requestId = TokenHasher.generateId();

        userRepository.findByEmail(EmailNormalizer.normalize(request.email()))
                .filter(User::canLogin)
                .ifPresent(user -> {
                    // 사용자별로 사용 가능한 코드는 하나만 유지한다.
                    resetRepository.findAllByUserIdAndUsedAtIsNull(user.getId())
                            .forEach(PasswordResetRequest::invalidate);

                    String code = ResetCodeGenerator.generateCode();
                    resetRepository.save(
                            PasswordResetRequest.issue(user, requestId, TokenHasher.hash(code))
                    );
                    mailSender.sendResetCode(user.getEmail(), code);
                });
        return new AuthDto.SendResetCodeResponse(
                requestId,
                PasswordResetRequest.CODE_TTL.toMillis(),
                PasswordResetRequest.MAX_ATTEMPTS
        );
    }

    // 2단계 - 코드 확인 (requestId로 행 찾기.)
    @Transactional(noRollbackFor = BaseException.class)
    public AuthDto.VerifyResetCodeResponse verifyCode(AuthDto.VerifyResetCodeRequest request) {
        PasswordResetRequest reset = resetRepository.findByRequestIdForUpdate(request.requestId())
                .orElseThrow(() -> new BaseException(BaseResponseStatus.REQUEST_ERROR));

        if(!reset.isCodeUsable()) {
            throw new BaseException(BaseResponseStatus.REQUEST_ERROR);
        }

        String code = ResetCodeGenerator.normalizeCode(request.code());
        if(!TokenHasher.matchesHash(code, reset.getTokenHash())) {
            reset.addFailedAttempt();
            throw new BaseException(BaseResponseStatus.REQUEST_ERROR);
        }

        String ticket = TokenHasher.generateToken();
        reset.markVerified(TokenHasher.hash(ticket));

        // 맞힐 경우 이메일을 보여줌.
        return new AuthDto.VerifyResetCodeResponse(
                ticket,
                maskEmail(reset.getUser().getEmail()),
                PasswordResetRequest.TICKET_TTL.toMillis()
        );
    }

    /** 3단계 - 새 비밀번호 설정
     * 성공하면 현재 사용 중인 모든 Refresh 토큰을 폐기한다.
     * 비밀번호 변경 후 기존 세션이 유지되지 않도록 하기 위함이다.
     */
    @Transactional
    public void resetPassword(AuthDto.ResetPasswordRequest request) {
        PasswordResetRequest reset = resetRepository
                .findByTicketHashForUpdate(TokenHasher.hash(request.ticket()))
                .orElseThrow(() -> new BaseException(BaseResponseStatus.REQUEST_ERROR));

        if(!reset.isTicketUsable()) {
            throw new BaseException(BaseResponseStatus.REQUEST_ERROR);
        }

        User user = reset.getUser();
        user.changePassword(passwordHasher.encodeNew(
                request.password(),
                request.passwordConfirm()));
        reset.markUsed();

        refreshRepository.findAllActiveForUpdate(user.getId())
                .forEach(refreshToken ->
                        refreshToken.revoke(RefreshRevokeReason.PASSWORD_RESET));
    }

    // 이메일 중간 가리기
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if(at <= 2) {
            return "**" + email.substring(at);
        }
        return email.substring(0, 2) + "**" + email.substring(at);
    }
}
