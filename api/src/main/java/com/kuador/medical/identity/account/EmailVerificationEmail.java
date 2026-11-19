package com.kuador.medical.identity.account;

import com.kuador.medical.notifications.EmailMessage;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Email verification message, in Spanish (it is read by the patient). The link carries the token
 * in its fragment (ADR-014) and opens the app or the web.
 */
final class EmailVerificationEmail {

    private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);

    private EmailVerificationEmail() {}

    static EmailMessage compose(
            LinkProperties links, String to, String firstNames, String clinicName, String token, Instant expiresAt) {
        String subject = "Confirma tu correo para %s en Medical Kuador".formatted(clinicName);
        String body = """
                Hola %s:

                Recibimos tu registro como paciente de %s. Para activar tu cuenta, confirma tu correo con este enlace:

                %s

                El enlace es de un solo uso y vence el %s (UTC). Hasta confirmarlo no podrás iniciar sesión.

                Si no te registraste, ignora este correo.
                """.formatted(firstNames, clinicName, links.withToken(links.emailVerificationPath(), token),
                EXPIRY.format(expiresAt));
        return new EmailMessage(to, subject, body);
    }
}
