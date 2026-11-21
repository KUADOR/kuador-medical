package com.kuador.medical.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.notifications.PlatformEmails.BillingPeriod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformEmailsTest {

    private static final String TO = "owner@clinic.example.test";
    private static final String CLINIC = "Clínica Demo";

    @Test
    void ownerInvitationCarriesTheOneTimeLinkAndItsExpiryInEcuadorTime() {
        EmailMessage message = PlatformEmails.ownerInvitation(
                TO, "Ana", CLINIC, "https://web.example.test/owner-invitation#t=tok_123", Instant.parse("2030-01-04T15:00:00Z"));

        assertThat(message.to()).isEqualTo(TO);
        assertThat(message.subject()).isEqualTo("Activa tu cuenta de Clínica Demo en Medical Kuador");
        assertThat(message.body())
                .startsWith("Hola Ana:")
                .contains("https://web.example.test/owner-invitation#t=tok_123")
                .contains("04/01/2030 10:00 (hora de Ecuador)")
                .contains("un solo uso")
                .contains("términos")
                .contains("verificación en dos pasos");
    }

    @Test
    void priceChangeNoticeShowsBothPricesWithoutVatAndTheDateItApplies() {
        EmailMessage message = PlatformEmails.priceChangeNotice(
                TO, CLINIC, "Plan Profesional", BillingPeriod.MONTHLY,
                new BigDecimal("49"), new BigDecimal("59.5"), "USD", LocalDate.parse("2030-03-01"));

        assertThat(message.subject()).isEqualTo("Cambio de tarifa del plan Plan Profesional");
        assertThat(message.body())
                .contains("USD 49.00")
                .contains("USD 59.50")
                .contains("mensual")
                .contains("01/03/2030")
                .contains("sin IVA");
    }

    @Test
    void annualPeriodIsWrittenInSpanish() {
        EmailMessage message = PlatformEmails.priceChangeNotice(
                TO, CLINIC, "Plan Profesional", BillingPeriod.ANNUAL,
                new BigDecimal("490.00"), new BigDecimal("590.00"), "USD", LocalDate.parse("2030-03-01"));

        assertThat(message.body()).contains("anual").doesNotContain("ANNUAL");
    }

    @Test
    void overdueNoticeKeepsFullAccessUntilTheEndOfGrace() {
        EmailMessage message = PlatformEmails.subscriptionOverdue(TO, CLINIC, LocalDate.parse("2030-02-08"));

        assertThat(message.subject()).isEqualTo("La suscripción de Clínica Demo está vencida");
        assertThat(message.body()).contains("acceso completo").contains("08/02/2030").contains("suspendida");
    }

    @Test
    void suspendedNoticeExplainsReadOnlyAndTheCancellationDate() {
        EmailMessage message = PlatformEmails.subscriptionSuspended(TO, CLINIC, LocalDate.parse("2030-05-09"));

        assertThat(message.subject()).isEqualTo("La suscripción de Clínica Demo está suspendida");
        assertThat(message.body()).contains("solo puede consultar y exportar").contains("09/05/2030").contains("cancelada");
    }

    @Test
    void cancelledNoticeExplainsTheRetentionWindow() {
        EmailMessage message = PlatformEmails.subscriptionCancelled(TO, CLINIC, LocalDate.parse("2030-08-07"));

        assertThat(message.subject()).isEqualTo("La suscripción de Clínica Demo fue cancelada");
        assertThat(message.body()).contains("07/08/2030").contains("exportar").contains("solo lectura");
    }

    @Test
    void reactivationNoticeNamesThePlan() {
        EmailMessage message = PlatformEmails.subscriptionReactivated(TO, CLINIC, "Plan Profesional");

        assertThat(message.subject()).isEqualTo("La suscripción de Clínica Demo fue reactivada");
        assertThat(message.body()).contains("Plan Profesional").contains("acceso completo");
    }

    @Test
    void twoFactorResetAsksToEnrollAgainAndWarnsIfUnexpected() {
        EmailMessage message = PlatformEmails.twoFactorReset(TO, CLINIC, "https://web.example.test/login");

        assertThat(message.subject()).isEqualTo("Se restableció tu verificación en dos pasos en Medical Kuador");
        assertThat(message.body())
                .contains("https://web.example.test/login")
                .contains("volver a configurar")
                .contains("Si no pediste este cambio");
    }

    @Test
    void everyTemplateIsSpanishAutomaticMailThatNeverPrintsItsContent() {
        List<EmailMessage> all = List.of(
                PlatformEmails.ownerInvitation(TO, "Ana", CLINIC, "https://x.test/#t=secret-token", Instant.EPOCH),
                PlatformEmails.priceChangeNotice(TO, CLINIC, "P", BillingPeriod.MONTHLY, BigDecimal.ONE, BigDecimal.TEN, "USD", LocalDate.EPOCH),
                PlatformEmails.subscriptionOverdue(TO, CLINIC, LocalDate.EPOCH),
                PlatformEmails.subscriptionSuspended(TO, CLINIC, LocalDate.EPOCH),
                PlatformEmails.subscriptionCancelled(TO, CLINIC, LocalDate.EPOCH),
                PlatformEmails.subscriptionReactivated(TO, CLINIC, "P"),
                PlatformEmails.twoFactorReset(TO, CLINIC, "https://x.test/login"));

        assertThat(all).allSatisfy(message -> {
            assertThat(message.body()).endsWith("Este es un correo automático de Medical Kuador; no respondas a este mensaje.\n");
            assertThat(message.toString()).doesNotContain(TO).doesNotContain(CLINIC).doesNotContain("secret-token");
        });
    }

    @Test
    void rejectsBlankRequiredValues() {
        assertThatThrownBy(() -> PlatformEmails.subscriptionOverdue(" ", CLINIC, LocalDate.EPOCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("to is required");
        assertThatThrownBy(() -> PlatformEmails.twoFactorReset(TO, CLINIC, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("loginLink is required");
    }
}
