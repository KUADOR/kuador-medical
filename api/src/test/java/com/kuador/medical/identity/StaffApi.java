package com.kuador.medical.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.CapturingNotificationSender;
import com.kuador.medical.notifications.EmailMessage;
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

/** Test client for {@code /staff-users} and the public invitation endpoints. Test data only. */
final class StaffApi {

    private static final Pattern INVITATION_LINK = Pattern.compile("(\\S*/invitation#t=([A-Za-z0-9_-]+))");

    private final MockMvcTester mvc;
    private final JsonMapper json;
    private final CapturingNotificationSender mail;

    StaffApi(MockMvcTester mvc, JsonMapper json, CapturingNotificationSender mail) {
        this.mvc = mvc;
        this.json = json;
        this.mail = mail;
    }

    static Map<String, Object> allBranches(Object roleId) {
        return Map.of("mode", "ALL_BRANCHES", "roleId", roleId.toString());
    }

    static Map<String, Object> perBranch(Object branchId, Object roleId) {
        return Map.of("mode", "PER_BRANCH",
                "assignments", List.of(Map.of("branchId", branchId.toString(), "roleId", roleId.toString())));
    }

    static Map<String, Object> newStaff(String email, Map<String, Object> assignments) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("firstNames", "Jane");
        request.put("lastNames", "Doe");
        request.put("email", email);
        request.put("jobTitle", "Front desk");
        request.put("roleAssignments", assignments);
        return request;
    }

    MvcTestResult create(String token, Map<String, Object> request) {
        return create(token, request, null);
    }

    MvcTestResult create(String token, Map<String, Object> request, @Nullable String idempotencyKey) {
        var builder = mvc.post().uri("/api/v1/staff-users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request));
        if (idempotencyKey != null) {
            builder = builder.header("Idempotency-Key", idempotencyKey);
        }
        return builder.exchange();
    }

    MvcTestResult list(String token, String query) {
        return mvc.get().uri("/api/v1/staff-users" + query).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    MvcTestResult get(String token, Object id) {
        return mvc.get().uri("/api/v1/staff-users/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    MvcTestResult patch(String token, Object id, Map<String, Object> body) {
        return mvc.patch().uri("/api/v1/staff-users/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .exchange();
    }

    MvcTestResult action(String token, Object id, String action) {
        return mvc.post().uri("/api/v1/staff-users/" + id + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();
    }

    MvcTestResult preview(String invitationToken) {
        return publicPost("/api/v1/auth/invitations/preview", Map.of("token", invitationToken));
    }

    MvcTestResult accept(String invitationToken, String password) {
        return publicPost("/api/v1/auth/invitations/accept", Map.of("token", invitationToken, "password", password));
    }

    private MvcTestResult publicPost(String uri, Object body) {
        return mvc.post().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .exchange();
    }

    List<EmailMessage> emailsTo(String address) {
        return mail.sentTo(address);
    }

    /** Token of the last invitation emailed to {@code address}, read from the fragment of its link. */
    String lastInvitationToken(String address) {
        List<EmailMessage> sent = mail.sentTo(address);
        assertThat(sent).as("emails to the invited address").isNotEmpty();
        Matcher matcher = INVITATION_LINK.matcher(sent.getLast().body());
        assertThat(matcher.find()).as("invitation link in the email").isTrue();
        return matcher.group(2);
    }

    static UUID id(Map<String, Object> body) {
        return UUID.fromString(body.get("id").toString());
    }
}
