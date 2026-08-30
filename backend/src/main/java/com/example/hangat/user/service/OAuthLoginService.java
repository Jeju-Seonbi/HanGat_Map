package com.example.hangat.user.service;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.config.security.oauth.OAuthProviderUser;
import com.example.hangat.config.security.token.TokenHasher;
import com.example.hangat.user.model.User;
import com.example.hangat.user.model.oauth.AuthProvider;
import com.example.hangat.user.model.oauth.OAuthLoginFlow;
import com.example.hangat.user.model.oauth.UserSocialAccount;
import com.example.hangat.user.model.dto.AuthInternalDto;
import com.example.hangat.user.repository.OAuthLoginFlowRepository;
import com.example.hangat.user.repository.UserRepository;
import com.example.hangat.user.repository.UserSocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구글이랑 카카오, OAuth 콜백 이후의 첫번째 분기 처리.
 *
 * 이미 provider UID가 연결되어 있으면 기존 사용자를 자동 로그인시킨다.
 * 처음 로그인한 UID라면 가입·연결용 OAuthLoginFlow를 생성한다.
 *
 * 구글은 괜찮지만 카카오의 경우 이메일 제공을 받을 수 없기 떄문에 까다롭다.
 * 또한 기존 계정이 있는지, 처음 가입하는 것인지에 따라 분기가 나뉨.
 */
@Service
@RequiredArgsConstructor
public class OAuthLoginService {

    private final UserSocialAccountRepository socialAccountRepository;
    private final OAuthLoginFlowRepository flowRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    // ────────────────────────── 콜백 로그인 분기 ──────────────────────────

    // ────────────────────────── 공급자별 흐름 생성 ──────────────────────────

    /**
     *  연결된 소셜 계정은 즉시 로그인 시키고
     *  처음 로그인한 계정은 추가 인증용 진행 상태를 생성.
     */
    @Transactional
    public AuthInternalDto.OAuthStartResult start(
            OAuthProviderUser providerUser) {

        UserSocialAccount linkedAccount =
                socialAccountRepository
                        .findByProviderAndProviderUid(
                                providerUser.provider(),
                                providerUser.providerUid()
                        ).orElse(null);

        if(linkedAccount != null) {
            AuthInternalDto.LoginResult loginResult =
                    authService.loginSocial(
                            linkedAccount.getUser());

            return AuthInternalDto
                    .OAuthStartResult
                    .loginCompleted(loginResult);
        }
        String rawFlowToken = TokenHasher.generateToken();

        String flowTokenHash = TokenHasher.hash(rawFlowToken);

        OAuthLoginFlow flow = createFlow(providerUser, flowTokenHash);

        flowRepository.save(flow);

        return AuthInternalDto.OAuthStartResult.onboarding(rawFlowToken);
    }

    /**
     * 공급자에 맞는 첫 OAuth 진행 상태를 생성한다.
     *
     * Google은 검증된 이메일과 동일한 한갓 계정을 조회한다.
     * Kakao는 이메일을 제공받지 못하므로 입력 화면부터 시작한다.
     */
    private OAuthLoginFlow createFlow(
            OAuthProviderUser providerUser,
            String flowTokenHash) {

        // 구글일 경우
        if(providerUser.provider() == AuthProvider.GOOGLE) {

            User existingUser = userRepository
                    .findByEmail(providerUser.verifiedEmail()).orElse(null);

            if(existingUser != null
                    && socialAccountRepository
                        .existsByUserIdAndProvider(
                                existingUser.getId()
                                , AuthProvider.GOOGLE)) {

                throw new BaseException(
                        BaseResponseStatus.SOCIAL_PROVIDER_ALREADY_LINKED
                );
            }

            return OAuthLoginFlow.startGoogle(
                    flowTokenHash,
                    providerUser.providerUid(),
                    providerUser.verifiedEmail(),
                    existingUser
            );
        }

        // 카카오일 경우
        if(providerUser.provider() == AuthProvider.KAKAO) {

            return OAuthLoginFlow.startKakao(
                    flowTokenHash,
                    providerUser.providerUid()
            );
        }

        throw new BaseException(
                BaseResponseStatus.OAUTH_LOGIN_FAILED
        );
    }
}
