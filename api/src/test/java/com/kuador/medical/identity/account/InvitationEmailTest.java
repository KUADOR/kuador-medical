package com.kuador.medical.identity.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.notifications.EmailMessage;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvitationEmailTest {

    private final LinkProperties links = new LinkProperties("https://web.example.test", "https://app.example.test/", "/invitation");

    @Test
    void isInSpanishWithTheTokenInTheFragmentOfTheAppLink() {
        EmailMessage message = InvitationEmail.compose(
                links, "jane@example.test", "Jane", "Clinica Demo", "tok_EN-123", Instant.parse("2030-01-04T15:00:00Z"));

        assertThat(message.to()).isEqualTo("jane@example.test");
        assertThat(message.subject()).isEqualTo("Invitación a Clinica Demo en Medical Kuador");
        assertThat(message.body())
                .startsWith("Hola Jane:")
                .contains("https://app.example.test/invitation#t=tok_EN-123")
                .contains("04/01/2030 15:00 (UTC)")
                .doesNotContain("?t=");
    }

    @Test
    void fallsBackToTheWebUrlWithoutAnAppLinkDomain() {
        LinkProperties webOnly = new LinkProperties("https://web.example.test", "", "/invitation");

        assertThat(InvitationEmail.link(webOnly, "abc")).isEqualTo("https://web.example.test/invitation#t=abc");
    }

    @Test
    void neverPrintsTheTokenInToString() {
        EmailMessage message = InvitationEmail.compose(
                links, "jane@example.test", "Jane", "Clinica Demo", "secret-token", Instant.parse("2030-01-04T15:00:00Z"));

        assertThat(message.toString()).doesNotContain("secret-token").doesNotContain("jane@example.test");
    }
}
