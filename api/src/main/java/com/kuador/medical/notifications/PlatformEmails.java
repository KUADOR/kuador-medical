package com.kuador.medical.notifications;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Spanish emails of the platform and subscription flows (MEDK-49): owner invitation, price change
 * notice, overdue, suspended, cancelled and reactivated subscription, and two-factor reset. They
 * are plain text, read by the clinic owner, and receive only display values so that this module
 * does not depend on the platform module. Send them through {@link EmailGateway}.
 */
public final class PlatformEmails {

    /** Billing period of a price, written in Spanish in the emails. */
    public enum BillingPeriod {
        MONTHLY("mensual"),
        ANNUAL("anual");

        private final String spanish;

        BillingPeriod(String spanish) {
            this.spanish = spanish;
        }
    }

    /** Dates and times are shown in the zone used for every subscription day count. */
    static final ZoneId ZONE = ZoneId.of("America/Guayaquil");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE);
    private static final String FOOTER = "Este es un correo automático de Medical Kuador; no respondas a este mensaje.\n";

    private PlatformEmails() {}

    /** Invitation of the initial OWNER: one-time link to set the password, accept terms and set up 2FA. */
    public static EmailMessage ownerInvitation(
            String to, String ownerName, String clinicName, String invitationLink, Instant expiresAt) {
        required(ownerName, "ownerName");
        required(clinicName, "clinicName");
        required(invitationLink, "invitationLink");
        Objects.requireNonNull(expiresAt, "expiresAt");
        return compose(to, "Activa tu cuenta de %s en Medical Kuador".formatted(clinicName), """
                Hola %s:

                Kuador registró a %s en Medical Kuador y te designó como propietario (OWNER) de la clínica.

                Para activar tu cuenta, abre este enlace:

                %s

                Allí definirás tu contraseña, aceptarás los términos de uso y la política de datos y configurarás la verificación en dos pasos con una aplicación de autenticación.

                El enlace es de un solo uso y vence el %s (hora de Ecuador). Si venció, pide a Kuador que te envíe una nueva invitación.

                Si no esperabas esta invitación, ignora este correo.
                """.formatted(ownerName, clinicName, invitationLink, DATE_TIME.format(expiresAt)));
    }

    /** Notice to the OWNER that the price of the plan changes from a later renewal (FR-005). */
    public static EmailMessage priceChangeNotice(
            String to,
            String clinicName,
            String planName,
            BillingPeriod period,
            BigDecimal currentAmount,
            BigDecimal newAmount,
            String currency,
            LocalDate appliesFrom) {
        required(clinicName, "clinicName");
        required(planName, "planName");
        required(currency, "currency");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(appliesFrom, "appliesFrom");
        return compose(to, "Cambio de tarifa del plan %s".formatted(planName), """
                Hola:

                Te informamos que la tarifa %s del plan %s, que tiene contratado %s, cambiará.

                Tarifa actual: %s
                Nueva tarifa: %s

                La nueva tarifa se aplicará desde la renovación del %s. Hasta entonces se mantiene el precio que contrataste. Los precios no incluyen IVA (sin IVA); el impuesto se detalla en la factura.

                Si tienes preguntas, comunícate con Kuador.
                """.formatted(
                        period.spanish, planName, clinicName,
                        money(currency, currentAmount), money(currency, newAmount), DATE.format(appliesFrom)));
    }

    /** The subscription is overdue (VENCIDA): full access with notices until the grace period ends. */
    public static EmailMessage subscriptionOverdue(String to, String clinicName, LocalDate graceEndsOn) {
        required(clinicName, "clinicName");
        Objects.requireNonNull(graceEndsOn, "graceEndsOn");
        return compose(to, "La suscripción de %s está vencida".formatted(clinicName), """
                Hola:

                La suscripción de %s en Medical Kuador está vencida porque no registramos el pago del período.

                Mientras tanto conservas el acceso completo. Si el pago no se registra hasta el %s, la suscripción quedará suspendida y solo podrás consultar y exportar la información.

                Si ya pagaste, ignora este aviso o comunícate con Kuador.
                """.formatted(clinicName, DATE.format(graceEndsOn)));
    }

    /** The subscription is suspended (SUSPENDIDA): read-only and export until it is cancelled. */
    public static EmailMessage subscriptionSuspended(String to, String clinicName, LocalDate cancelsOn) {
        required(clinicName, "clinicName");
        Objects.requireNonNull(cancelsOn, "cancelsOn");
        return compose(to, "La suscripción de %s está suspendida".formatted(clinicName), """
                Hola:

                La suscripción de %s en Medical Kuador está suspendida. Tus datos se conservan intactos, pero por ahora la clínica solo puede consultar y exportar su información: no puede crear ni modificar registros.

                Para reactivarla, comunícate con Kuador. Si no se reactiva, la suscripción será cancelada el %s.
                """.formatted(clinicName, DATE.format(cancelsOn)));
    }

    /** The subscription is cancelled (CANCELADA): read-only and export during the data retention. */
    public static EmailMessage subscriptionCancelled(String to, String clinicName, LocalDate retentionEndsOn) {
        required(clinicName, "clinicName");
        Objects.requireNonNull(retentionEndsOn, "retentionEndsOn");
        return compose(to, "La suscripción de %s fue cancelada".formatted(clinicName), """
                Hola:

                La suscripción de %s en Medical Kuador fue cancelada.

                Conservaremos los datos de la clínica hasta el %s. Durante ese tiempo tendrás acceso de solo lectura y podrás exportar la información. Después, los datos podrán anonimizarse o eliminarse según la ley.

                Si quieres reactivar la suscripción antes de esa fecha, comunícate con Kuador.
                """.formatted(clinicName, DATE.format(retentionEndsOn)));
    }

    /** The subscription is active again after a suspension or a cancellation. */
    public static EmailMessage subscriptionReactivated(String to, String clinicName, String planName) {
        required(clinicName, "clinicName");
        required(planName, "planName");
        return compose(to, "La suscripción de %s fue reactivada".formatted(clinicName), """
                Hola:

                La suscripción de %s en Medical Kuador fue reactivada con el plan %s.

                La clínica recupera el acceso completo con sus datos intactos.
                """.formatted(clinicName, planName));
    }

    /** Kuador reset the OWNER's two-factor authentication: they enroll again at their next sign-in. */
    public static EmailMessage twoFactorReset(String to, String clinicName, String loginLink) {
        required(clinicName, "clinicName");
        required(loginLink, "loginLink");
        return compose(to, "Se restableció tu verificación en dos pasos en Medical Kuador", """
                Hola:

                Kuador restableció la verificación en dos pasos de tu cuenta de propietario de %s.

                La próxima vez que inicies sesión deberás volver a configurarla con tu aplicación de autenticación:

                %s

                Si no pediste este cambio, comunícate de inmediato con Kuador.
                """.formatted(clinicName, loginLink));
    }

    private static EmailMessage compose(String to, String subject, String body) {
        required(to, "to");
        return new EmailMessage(to.strip(), subject, body + "\n" + FOOTER);
    }

    private static String money(String currency, BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        return currency + " " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
