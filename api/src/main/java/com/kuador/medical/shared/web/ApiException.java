package com.kuador.medical.shared.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * An error the API reports to the client as Problem Details with a stable {@link ErrorCode}.
 * The detail is meant for developers and must never contain secrets or personal data.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final List<FieldViolation> errors;
    private final Map<String, String> properties;

    public ApiException(HttpStatus status, ErrorCode code, String detail) {
        this(status, code, detail, List.of(), Map.of());
    }

    private ApiException(
            HttpStatus status, ErrorCode code, String detail, List<FieldViolation> errors, Map<String, String> properties) {
        super(detail);
        this.status = status;
        this.code = code;
        this.errors = List.copyOf(errors);
        this.properties = Map.copyOf(properties);
    }

    public HttpStatus status() {
        return status;
    }

    public ErrorCode code() {
        return code;
    }

    /** Field-level errors; only {@link ErrorCode#VALIDATION} carries them. */
    public List<FieldViolation> errors() {
        return errors;
    }

    /** Extra members of the Problem Details, for example {@code twoFactor} of {@code TWO_FACTOR_REQUIRED}. */
    public Map<String, String> properties() {
        return properties;
    }

    /** 400 {@code VALIDATION} for a rule that Bean Validation cannot express, with its fields. */
    public static ApiException validation(List<FieldViolation> errors) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, "The request is not valid.", errors, Map.of());
    }

    /** 409 {@code VALIDATION} for a field that clashes with existing data, for example a duplicate code. */
    public static ApiException validationConflict(List<FieldViolation> errors) {
        return new ApiException(
                HttpStatus.CONFLICT, ErrorCode.VALIDATION, "The request conflicts with existing data.", errors, Map.of());
    }

    public static ApiException conflict(ErrorCode code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, detail);
    }

    public static ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Authentication is required.");
    }

    public static ApiException passwordChangeRequired() {
        return new ApiException(
                HttpStatus.FORBIDDEN, ErrorCode.PASSWORD_CHANGE_REQUIRED, "The password must be changed first.");
    }

    /**
     * 401 {@code TWO_FACTOR_REQUIRED} (ADR-019): the session has not passed the second factor;
     * {@code step} ({@code ENROLLMENT_REQUIRED} or {@code VERIFICATION_REQUIRED}) tells the client what to do.
     */
    public static ApiException twoFactorRequired(String step) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.TWO_FACTOR_REQUIRED,
                "The second factor must be completed first.", List.of(), Map.of("twoFactor", step));
    }

    /** 403 {@code TENANT_READ_ONLY} (ADR-017): the clinic is suspended or cancelled; it can only read and export. */
    public static ApiException tenantReadOnly() {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.TENANT_READ_ONLY,
                "The clinic is read-only: it can only read and export its data.");
    }

    /** 403 {@code MODULE_NOT_IN_PLAN} (ADR-017): the plan of the clinic does not enable {@code module}. */
    public static ApiException moduleNotInPlan(String module) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.MODULE_NOT_IN_PLAN,
                "The plan of the clinic does not include the module " + module + ".");
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "The resource does not exist.");
    }
}
