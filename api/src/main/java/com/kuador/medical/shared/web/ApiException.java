package com.kuador.medical.shared.web;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * An error the API reports to the client as Problem Details with a stable {@link ErrorCode}.
 * The detail is meant for developers and must never contain secrets or personal data.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final List<FieldViolation> errors;

    public ApiException(HttpStatus status, ErrorCode code, String detail) {
        this(status, code, detail, List.of());
    }

    private ApiException(HttpStatus status, ErrorCode code, String detail, List<FieldViolation> errors) {
        super(detail);
        this.status = status;
        this.code = code;
        this.errors = List.copyOf(errors);
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

    /** 400 {@code VALIDATION} for a rule that Bean Validation cannot express, with its fields. */
    public static ApiException validation(List<FieldViolation> errors) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, "The request is not valid.", errors);
    }

    /** 409 {@code VALIDATION} for a field that clashes with existing data, for example a duplicate code. */
    public static ApiException validationConflict(List<FieldViolation> errors) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.VALIDATION, "The request conflicts with existing data.", errors);
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

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "The resource does not exist.");
    }
}
