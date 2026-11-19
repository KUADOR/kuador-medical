package com.kuador.medical.identity.account;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Minimum password policy: configurable length (12 by default) and different from the current one. */
class PasswordPolicyTest {

    private final PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final PasswordPolicy policy = new PasswordPolicy(new PasswordProperties(12), encoder);
    private final String currentHash = encoder.encode("the-current-passphrase");

    @Test
    void acceptsALongEnoughNewPassword() {
        assertThatCode(() -> policy.checkNewPassword("twelve-chars", currentHash)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAShortPassword() {
        assertWeak("elevenchars");
    }

    @Test
    void rejectsTheCurrentPassword() {
        assertWeak("the-current-passphrase");
    }

    @Test
    void lengthIsConfigurable() {
        var strict = new PasswordPolicy(new PasswordProperties(20), encoder);

        assertThatThrownBy(() -> strict.checkNewPassword("nineteen-characters", currentHash))
                .isInstanceOf(ApiException.class);
    }

    private void assertWeak(String password) {
        assertThatThrownBy(() -> policy.checkNewPassword(password, currentHash))
                .isInstanceOfSatisfying(ApiException.class, e -> org.assertj.core.api.Assertions
                        .assertThat(e.code()).isEqualTo(ErrorCode.WEAK_PASSWORD));
    }
}
