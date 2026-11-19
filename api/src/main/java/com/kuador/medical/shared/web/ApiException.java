package com.kuador.medical.shared.web;

import org.springframework.http.HttpStatus;

/**
 * An error the API reports to the client as Problem Details with a stable {@link ErrorCode}.
 * The detail is meant for developers and must never contain secrets or personal data.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    public ApiException(HttpStatus status, ErrorCode code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public ErrorCode code() {
        return code;
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
