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

    /** Logs in and returns the access token. */
    public String token(String email, String password) {
        MvcTestResult result = login(email, password);
        assertThat(result).hasStatus(HttpStatus.OK);
        return body(result).get("accessToken").toString();
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
