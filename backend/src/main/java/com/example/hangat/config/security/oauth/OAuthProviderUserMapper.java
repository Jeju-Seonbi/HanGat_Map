package com.example.hangat.config.security.oauth;

import com.example.hangat.common.exception.BaseException;
import com.example.hangat.common.model.BaseResponseStatus;
import com.example.hangat.common.util.EmailNormalizer;
import com.example.hangat.user.model.oauth.AuthProvider;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 *  스프링 시큐리티가 받은 Oauth 유저들을 OAuthProviderUser로 반환한다.
 *
 *  구글의 경우 - sub, email, email_verified를 확인하고
 *  카카오의 경우 - id만 필수로 사용하고 kakao_account.email은 사용하지 않는다.
 */
@Component
public class OAuthProviderUserMapper {

    public OAuthProviderUser map(
            OAuth2AuthenticationToken authentication) {

        String registrationId = authentication
                .getAuthorizedClientRegistrationId()
                .toLowerCase(Locale.ROOT);

        Map<String, Object> attributes =
                authentication.getPrincipal().getAttributes();

        return switch (registrationId) {
            case "google" -> mapGoogle(attributes);
            case "kakao" -> mapKakao(attributes);
            default -> throw oauthLoginFailed();
        };
    }

    // ────────────────────────── 구글이랑 카카오 처리 ──────────────────────────

    private OAuthProviderUser mapGoogle(Map<String, Object> attributes) {

        String providerUid = requiredText(attributes.get("sub"));
        String email = requiredText(attributes.get("email"));

        Object verifiedValue = attributes.get("email_verified");
        boolean emailVerified =
                Boolean.TRUE.equals(verifiedValue)
                        || "true".equalsIgnoreCase(
                                String.valueOf(verifiedValue)
                );

        if(!emailVerified) {
            throw oauthLoginFailed();
        }

        String normalizedEmail = EmailNormalizer.normalize(email);

        if(normalizedEmail.length() > 255) {
            throw oauthLoginFailed();
        }

        return new OAuthProviderUser(
                AuthProvider.GOOGLE,
                providerUid,
                normalizedEmail
        );
    }
    private OAuthProviderUser mapKakao(
            Map<String, Object> attributes) {

        String providerUid = requiredText(attributes.get("id"));

        return new OAuthProviderUser(
                AuthProvider.KAKAO,
                providerUid,
                null
        );
    }

    // ────────────────────────── 예외 처리 및 텍스트 처리 ──────────────────────────

    private String requiredText(Object value) {
        if(value == null) {
            throw oauthLoginFailed();
        }

        String text = String.valueOf(value).trim();

        if(text.isEmpty() || text.length() > 255) {
            throw oauthLoginFailed();
        }

        return text;
    }

    private BaseException oauthLoginFailed() {
        return new BaseException(
                BaseResponseStatus.OAUTH_LOGIN_FAILED
        );
    }
}
