package com.example.hangat.common.util;

import org.springframework.stereotype.Component;

/**
 *  기존 계정의 원본 이메일을 마스킹 처리함.
 *  프론트에서도 해도 되지만 프론트에서 할 경우 결국 원본 이메일을 보내는 의미이기 때문에
 *  백엔드에서도 처리를 한다.
 */
@Component
public class EmailMasker {
    public String mask(String email) {
        if (email == null) {
            return null;
        }

        int at = email.indexOf('@');

        if (at <= 0) {
            return "***";
        }

        String local = email.substring(0, at);
        String domain = email.substring(at);

        if (local.length() == 1) {
            return "*" + domain;
        }

        if (local.length() == 2) {
            return local.charAt(0) + "*" + domain;
        }

        if (local.length() <= 4) {
            return local.charAt(0)
                    + "*".repeat(local.length() - 2)
                    + local.charAt(local.length() - 1)
                    + domain;
        }

        return local.substring(0, 2)
                + "*".repeat(local.length() - 4)
                + local.substring(local.length() - 2)
                + domain;
    }
}