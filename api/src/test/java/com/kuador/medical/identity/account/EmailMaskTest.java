package com.kuador.medical.identity.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailMaskTest {

    @Test
    void keepsTheFirstLetterAndTheDomain() {
        assertThat(EmailAddresses.mask("jane.doe@example.com")).isEqualTo("j***@example.com");
        assertThat(EmailAddresses.mask("a@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    void masksMalformedAddressesCompletely() {
        assertThat(EmailAddresses.mask("no-at-sign")).isEqualTo("***");
    }
}
