package com.kuador.medical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test client for the identity endpoints: signup, login, password change and current account.
 * Every value it generates is random test data, never a real credential.
 */
public final class IdentityApi {

    private final MockMvcTester mvc;
    private final JsonMapper json;

    public IdentityApi(MockMvcTester mvc, JsonMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.test";
    }

    public static String randomPassword() {
        return "pw-" + UUID.randomUUID();
    }

    public static Map<String, Object> signupRequest(String clinicName, String email, String password) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("clinicName", clinicName);
        request.put("ownerDisplayName", "Owner of " + clinicName);
        request.put("email", email);
        request.put("password", password);
        return request;
    }

    public MvcTestResult signUp(Map<String, Object> request) {
        return mvc.post().uri("/api/v1/tenants/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request))
                .exchange();
    }

    /** Signs up a clinic and returns its tenant id. */
    public UUID signUp(String clinicName, String email, String password) {
        MvcTestResult result = signUp(signupRequest(clinicName, email, password));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(body(result).get("tenantId").toString());
    }

    public MvcTestResult login(String email, String password) {
        return mvc.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password)))
                .exchange();
    }

    /**
     * Logs in and returns the access token. A branch selection picks the first listed branch; a
     * clinic selection is a test error (use {@link #login} and {@link #selectClinic} instead).
     */
    public String token(String email, String password) {
        return session(email, password).get("accessToken").toString();
    }

    /** Logs in (choosing the first branch when asked) and returns the AUTHENTICATED body. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> session(String email, String password) {
        MvcTestResult result = login(email, password);
        assertThat(result).hasStatus(HttpStatus.OK);
        Map<String, Object> body = body(result);
        if ("BRANCH_SELECTION_REQUIRED".equals(body.get("status"))) {
            List<Map<String, Object>> branches = (List<Map<String, Object>>) body.get("branches");
            MvcTestResult selected = selectBranch(body.get("preAuthToken").toString(), branches.getFirst().get("id").toString());
            assertThat(selected).hasStatus(HttpStatus.OK);
            body = body(selected);
        }
        assertThat(body).containsEntry("status", "AUTHENTICATED");
        return body;
    }

    public MvcTestResult selectClinic(String preAuthToken, Object clinicId) {
        return publicPost("/api/v1/auth/login/clinic", Map.of("preAuthToken", preAuthToken, "clinicId", clinicId.toString()));
    }

    public MvcTestResult selectBranch(String preAuthToken, Object branchId) {
        return publicPost("/api/v1/auth/login/branch", Map.of("preAuthToken", preAuthToken, "branchId", branchId.toString()));
    }

    public MvcTestResult refresh(String refreshToken) {
        return publicPost("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken));
    }

    public MvcTestResult logout(String refreshToken) {
        return publicPost("/api/v1/auth/logout", Map.of("refreshToken", refreshToken));
    }

    public MvcTestResult requestPasswordReset(String email) {
        return publicPost("/api/v1/auth/password-reset/request", Map.of("email", email));
    }

    public MvcTestResult confirmPasswordReset(String token, String newPassword) {
        return publicPost("/api/v1/auth/password-reset/confirm", Map.of("token", token, "newPassword", newPassword));
    }

    public MvcTestResult switchBranch(String accessToken, Object branchId, String refreshToken) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("branchId", branchId.toString());
        if (refreshToken != null) {
            request.put("refreshToken", refreshToken);
        }
        return post("/api/v1/auth/switch-branch", accessToken, request);
    }

    public MvcTestResult publicPost(String uri, Object body) {
        return mvc.post().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .exchange();
    }

    /** Claims of a JWT, decoded without verifying it (the api verifies; tests only read). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> claims(String jwt) {
        return json.readValue(java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), Map.class);
    }

    public MvcTestResult changePassword(String token, String currentPassword, String newPassword) {
        return mvc.post().uri("/api/v1/auth/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", currentPassword, "newPassword", newPassword)))
                .exchange();
    }

    public MvcTestResult me(String token) {
        return get("/api/v1/me", token);
    }

    public MvcTestResult get(String uri, String token) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    public MvcTestResult post(String uri, String token, Object body) {
        return mvc.post().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .exchange();
    }

    public MvcTestResult put(String uri, String token, Object body) {
        return mvc.put().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .exchange();
    }

    public MvcTestResult patch(String uri, String token, Object body) {
        return mvc.patch().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))
                .exchange();
    }

    public MvcTestResult delete(String uri, String token) {
        return mvc.delete().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
    }

    /** Signs up a clinic and logs its owner in. */
    public Clinic clinic(String clinicName) {
        String email = uniqueEmail("owner");
        String password = randomPassword();
        UUID tenantId = signUp(clinicName, email, password);
        return new Clinic(tenantId, token(email, password));
    }

    /** A signed-up clinic and the access token of its owner (OWNER and ADMIN). */
    public record Clinic(UUID tenantId, String token) {}

    @SuppressWarnings("unchecked")
    public Map<String, Object> body(MvcTestResult result) {
        return json.readValue(result.getResponse().getContentAsByteArray(), Map.class);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> bodyList(MvcTestResult result) {
        return json.readValue(result.getResponse().getContentAsByteArray(), List.class);
    }
}
