package com.kuador.medical.platform.offboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.platform.offboarding.ExportDownloadGrants.Grant;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Signed, short-lived download links of an export (MEDK-70). */
class ExportDownloadGrantsTest {

    private static final Instant NOW = Instant.parse("2030-07-01T15:00:00Z");
    private static final byte[] SECRET = "test-only-secret-with-at-least-32-bytes!".getBytes(StandardCharsets.UTF_8);

    private final ExportDownloadGrants grants = new ExportDownloadGrants(SECRET);
    private final Grant grant = new Grant(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(300));

    @Test
    void aGrantIsValidUntilItExpires() {
        String value = grants.issue(grant);

        assertThat(grants.verify(value, NOW)).contains(grant);
        assertThat(grants.verify(value, NOW.plusSeconds(299))).contains(grant);
        assertThat(grants.verify(value, NOW.plusSeconds(300))).isEmpty();
    }

    @Test
    void aTamperedOrForeignGrantIsRejected() {
        String value = grants.issue(grant);
        Grant otherClinic = new Grant(UUID.randomUUID(), grant.exportId(), grant.accountId(), grant.expiresAt());
        String forged = grants.issue(otherClinic).substring(0, value.indexOf('.')) + value.substring(value.indexOf('.'));
        ExportDownloadGrants otherKey = new ExportDownloadGrants(
                "another-secret-with-at-least-32-bytes!!!".getBytes(StandardCharsets.UTF_8));

        assertThat(grants.verify(forged, NOW)).as("payload of another clinic with this signature").isEmpty();
        assertThat(otherKey.verify(value, NOW)).as("signed with another key").isEmpty();
        assertThat(grants.verify(value.substring(0, value.length() - 2) + "AA", NOW)).isEmpty();
        assertThat(grants.verify("not-a-grant", NOW)).isEmpty();
        assertThat(grants.verify("%%%.###", NOW)).isEmpty();
        assertThat(grants.verify("", NOW)).isEmpty();
    }

    @Test
    void theSecretMustBeLongEnough() {
        assertThatThrownBy(() -> new ExportDownloadGrants(new byte[16])).isInstanceOf(IllegalStateException.class);
    }
}
