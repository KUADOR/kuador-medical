package com.kuador.medical.identity.owner;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import org.junit.jupiter.api.Test;

/** FR-009: a clinic always keeps an active OWNER; the last one cannot be removed nor demoted. */
class LastOwnerGuardTest {

    @Test
    void theLastActiveOwnerCannotLoseTheRole() {
        assertThatThrownBy(() -> LastOwnerGuard.check(true, true, 1))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo(ErrorCode.LAST_OWNER);
                    org.assertj.core.api.Assertions.assertThat(error.status().value()).isEqualTo(409);
                });
    }

    @Test
    void anOwnerWithAnotherActiveOwnerOrANonOwnerCanLoseIt() {
        assertThatCode(() -> LastOwnerGuard.check(true, true, 2)).doesNotThrowAnyException();
        assertThatCode(() -> LastOwnerGuard.check(false, true, 1)).doesNotThrowAnyException();
        assertThatCode(() -> LastOwnerGuard.check(true, false, 1))
                .as("an owner that is not active does not keep the clinic operating")
                .doesNotThrowAnyException();
    }
}
