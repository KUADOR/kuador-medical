package com.kuador.medical.identity.account;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** A new password needs the minimum length and must differ from the current one. */
@Component
public class PasswordPolicy {

    private final PasswordProperties properties;
    private final PasswordEncoder passwordEncoder;

    public PasswordPolicy(PasswordProperties properties, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    /** First password of an account (invitation): only the minimum length applies. */
    public void checkFirstPassword(String newPassword) {
        if (newPassword.codePointCount(0, newPassword.length()) < properties.minLength()) {
            throw weak();
        }
    }

    /** @throws ApiException {@code WEAK_PASSWORD} when {@code newPassword} breaks the policy */
    public void checkNewPassword(String newPassword, String currentPasswordHash) {
        if (newPassword.codePointCount(0, newPassword.length()) < properties.minLength()
                || passwordEncoder.matches(newPassword, currentPasswordHash)) {
            throw weak();
        }
    }

    private ApiException weak() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.WEAK_PASSWORD,
                "The new password must have at least %d characters and differ from the current one."
                        .formatted(properties.minLength()));
    }
}
