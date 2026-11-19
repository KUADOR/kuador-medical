package com.kuador.medical.patients;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.CapturingNotificationSender;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.identity.StaffFixture;
import com.kuador.medical.notifications.EmailMessage;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/** Test client of the patients endpoints. Every value is fictitious test data. */
final class PatientApi {

    private static final Pattern VERIFICATION_LINK = Pattern.compile("/email-verification#t=([A-Za-z0-9_-]+)");

    private final MockMvcTester mvc;
    private final JsonMapper json;
    private final CapturingNotificationSender mail;

    PatientApi(MockMvcTester mvc, JsonMapper json, CapturingNotificationSender mail) {
        this.mvc = mvc;
        this.json = json;
        this.mail = mail;
    }

    static String uniqueDocument() {
        return String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits())).substring(0, 10);
    }

    /** An adult patient with every mandatory field and the consent. */
    static Map<String, Object> adult() {
        return patient(LocalDate.now().minusYears(30));
    }

    /** A minor (10 years old) with a new legal guardian and the consent granted by the guardian. */
    static Map<String, Object> minor() {
        Map<String, Object> request = patient(LocalDate.now().minusYears(10));
        request.put("consent", consent("GUARDIAN"));
        request.put("guardian", Map.of("relationship", "Mother", "person", guardianPerson()));
        return request;
    }

    static Map<String, Object> patient(LocalDate birthDate) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("documentType", "NATIONAL_ID");
        request.put("documentNumber", uniqueDocument());
        request.put("firstNames", "John");
        request.put("lastNames", "Roe");
        request.put("birthDate", birthDate.toString());
        request.put("sex", "MALE");
        request.put("consent", consent("PATIENT"));
        return request;
    }

    static Map<String, Object> consent(String grantedBy) {
        return Map.of("textVersion", "2026-01", "grantedBy", grantedBy);
    }

    static Map<String, Object> guardianPerson() {
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("documentType", "NATIONAL_ID");
        person.put("documentNumber", uniqueDocument());
        person.put("firstNames", "Mary");
        person.put("lastNames", "Roe");
        person.put("email", IdentityApi.uniqueEmail("guardian"));
        return person;
    }

    static Map<String, Object> selfRegistration(String email, String password) {
        Map<String, Object> request = patient(LocalDate.now().minusYears(25));
        request.put("email", email);
        request.put("password", password);
        request.put("consent", Map.of("textVersion", "2026-01"));
        return request;
    }

    MvcTestResult register(String token, Map<String, Object> request) {
        return register(token, request, null);
    }

    MvcTestResult register(String token, Map<String, Object> request, @Nullable String idempotencyKey) {
        return post("/api/v1/patients", token, request, idempotencyKey);
    }

    MvcTestResult list(String token, String query) {
        return get("/api/v1/patients" + query, token);
    }

    MvcTestResult get(String token, Object id) {
        return get("/api/v1/patients/" + id, token);
    }

    MvcTestResult patch(String token, Object id, Map<String, Object> body) {
        return mvc.patch().uri("/api/v1/patients/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .exchange();
    }

    MvcTestResult recordConsent(String token, Object id, Map<String, Object> body, @Nullable String idempotencyKey) {
        return post("/api/v1/patients/" + id + "/consents", token, body, idempotencyKey);
    }

    MvcTestResult links(String token, Object id) {
        return get("/api/v1/patients/" + id + "/guardian-links", token);
    }

    MvcTestResult createLink(String token, Object id, Map<String, Object> body, @Nullable String idempotencyKey) {
        return post("/api/v1/patients/" + id + "/guardian-links", token, body, idempotencyKey);
    }

    MvcTestResult endLink(String token, Object id, Object linkId) {
        return post("/api/v1/patients/" + id + "/guardian-links/" + linkId + "/end", token, null, null);
    }

    MvcTestResult inviteAccount(String token, Object id, String channel) {
        return post("/api/v1/patients/" + id + "/account-invitation", token, Map.of("channel", channel), null);
    }

    MvcTestResult settings(String token) {
        return get("/api/v1/patient-registration-settings", token);
    }

    MvcTestResult updateSettings(String token, boolean enabled, long version) {
        return mvc.put().uri("/api/v1/patient-registration-settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("selfRegistrationEnabled", enabled, "version", version)))
                .exchange();
    }

    MvcTestResult selfRegister(String publicCode, Map<String, Object> request, @Nullable String idempotencyKey) {
        var builder = mvc.post().uri("/api/v1/public/clinics/" + publicCode + "/patient-registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request));
        if (idempotencyKey != null) {
            builder = builder.header("Idempotency-Key", idempotencyKey);
        }
        return builder.exchange();
    }

    MvcTestResult confirmEmail(String token) {
        return mvc.post().uri("/api/v1/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token)))
                .exchange();
    }

    /** Token of the last verification link emailed to {@code address}. */
    String lastVerificationToken(String address) {
        List<EmailMessage> sent = mail.awaitSentTo(address, 1);
        assertThat(sent).as("emails to %s", address).isNotEmpty();
        Matcher matcher = VERIFICATION_LINK.matcher(sent.getLast().body());
        assertThat(matcher.find()).as("verification link in the email").isTrue();
        return matcher.group(1);
    }

    List<EmailMessage> emailsTo(String address) {
        return mail.sentTo(address);
    }

    /** Access token of a new active staff user holding the suggested role in every branch given. */
    static String staffToken(IdentityApi api, StaffFixture staff, UUID tenantId, UUID branchId, String suggestedRole) {
        String email = IdentityApi.uniqueEmail(suggestedRole.toLowerCase(java.util.Locale.ROOT));
        String password = IdentityApi.randomPassword();
        UUID user = staff.staffUser(tenantId, email, password);
        staff.assign(tenantId, user, branchId, staff.role(tenantId, suggestedRole));
        return api.token(email, password);
    }

    static UUID id(Map<String, Object> body) {
        return UUID.fromString(body.get("id").toString());
    }

    private MvcTestResult get(String uri, String token) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    private MvcTestResult post(String uri, String token, @Nullable Object body, @Nullable String idempotencyKey) {
        var builder = mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        if (idempotencyKey != null) {
            builder = builder.header("Idempotency-Key", idempotencyKey);
        }
        return builder.exchange();
    }

    static Map<String, Object> copy(Map<String, Object> source) {
        return new LinkedHashMap<>(source);
    }
}
