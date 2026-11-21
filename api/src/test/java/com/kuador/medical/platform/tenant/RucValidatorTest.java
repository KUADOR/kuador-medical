package com.kuador.medical.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Plan D8: a RUC has exactly 13 digits. Uniqueness is covered by {@code PlatformTenantsIT}. */
class RucValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"1790000000001", "0000000000001", "0999999999001"})
    void thirteenDigitsAreAValidFormat(String ruc) {
        assertThat(RucValidator.hasValidFormat(ruc)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "179000000000", "17900000000011", "17900000000A1", " 1790000000001", "1790000000001\n",
            "１７９００００００００１"})
    void anythingElseIsInvalid(String ruc) {
        assertThat(RucValidator.hasValidFormat(ruc)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void nullIsInvalid() {
        assertThat(RucValidator.hasValidFormat(null)).isFalse();
    }
}
