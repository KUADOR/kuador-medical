package com.kuador.medical.identity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kuador.medical.identity.account.UserAccount;
import com.kuador.medical.identity.account.UserAccountRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Initial SUPER_ADMIN creation rules; the email and password never reach the logs. */
@ExtendWith(OutputCaptureExtension.class)
class BootstrapAdminInitializerTest {

    private static final String EMAIL = "admin@example.test";

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final String password = "initial-" + UUID.randomUUID();

    @Test
    void createsAPlatformAdminThatMustChangeItsPassword(CapturedOutput output) {
        initializer(EMAIL.toUpperCase(), password).createIfMissing();

        var saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().isPlatformAdmin()).isTrue();
        assertThat(saved.getValue().isMustChangePassword()).isTrue();
        assertThat(encoder.matches(password, saved.getValue().getPasswordHash())).isTrue();
        assertThat(output.getAll()).doesNotContain(password).doesNotContainIgnoringCase(EMAIL);
    }

    @Test
    void doesNothingWhenAPlatformAdminExists() {
        when(accounts.existsByPlatformAdminTrue()).thenReturn(true);

        initializer(EMAIL, password).createIfMissing();

        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void startsWithoutTheAccountWhenVariablesAreMissing(CapturedOutput output) {
        initializer("", password).createIfMissing();
        initializer(EMAIL, null).createIfMissing();

        verify(accounts, never()).saveAndFlush(any());
        assertThat(output.getAll()).contains("KUADOR_BOOTSTRAP_ADMIN_EMAIL").doesNotContain(password);
    }

    @Test
    void neverPromotesAnExistingClinicAccount(CapturedOutput output) {
        when(accounts.existsByEmail(EMAIL)).thenReturn(true);

        initializer(EMAIL, password).createIfMissing();

        verify(accounts, never()).saveAndFlush(any());
        assertThat(output.getAll()).doesNotContain(EMAIL).doesNotContain(password);
    }

    @Test
    void propertiesNeverPrintTheirValues() {
        assertThat(new BootstrapAdminProperties(EMAIL, password).toString())
                .doesNotContain(EMAIL)
                .doesNotContain(password);
    }

    private BootstrapAdminInitializer initializer(String email, String password) {
        return new BootstrapAdminInitializer(new BootstrapAdminProperties(email, password), accounts, encoder);
    }
}
