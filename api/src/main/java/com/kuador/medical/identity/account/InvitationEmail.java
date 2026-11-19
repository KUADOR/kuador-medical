package com.kuador.medical.identity.account;

import com.kuador.medical.notifications.EmailMessage;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Invitation email, in Spanish (it is read by the invited person). The link carries the token in
 * its fragment (ADR-014) and opens the app or the web.
 */
final class InvitationEmail {

    private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);

    private InvitationEmail() {}

    static EmailMessage compose(
            LinkProperties links, String to, String firstNames, String clinicName, String token, Instant expiresAt) {
        String subject = "Invitación a %s en Medical Kuador".formatted(clinicName);
        String body = """
                Hola %s:

                %s te invitó a usar Medical Kuador. Para activar tu cuenta y elegir tu contraseña, abre este enlace:

                %s

                El enlace es de un solo uso y vence el %s (UTC). Si venció, pide a la clínica que te envíe una nueva invitación.

                Si no esperabas esta invitación, ignora este correo.
                """.formatted(firstNames, clinicName, link(links, token), EXPIRY.format(expiresAt));
        return new EmailMessage(to, subject, body);
    }

    static String link(LinkProperties links, String token) {
        return links.withToken(links.invitationPath(), token);
    }
}
