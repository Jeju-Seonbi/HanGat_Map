package com.example.hangat.common.util;

import java.util.Locale;

/**
 * 이메일 정규화.
 *
 * 우리 이메일은 현재 하나의 계정에만 되기때문에 각기 다른 계정이 될 수 있다.
 * 예를 들어 이메일로 hong@email.com으로 했는데 만약 Hong@email.com으로 된다면
 * 둘은 각기 다른 계정이 된다.
 */

public final class EmailNormalizer {

    public static String normalize(String raw) {
        if(raw == null) return null;

        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
