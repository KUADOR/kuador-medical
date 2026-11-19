package com.kuador.medical.identity.auth;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

/** The errors of the login and session flows; every message is generic. */
final class SessionErrors {

    private SessionErrors() {}

    static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Invalid email or password.");
    }

    static ApiException invalidSessionToken() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_SESSION_TOKEN, "The session token is not valid.");
    }

    static ApiException branchNotAssigned() {
        return new ApiException(
                HttpStatus.FORBIDDEN, ErrorCode.BRANCH_NOT_ASSIGNED, "The account has no assignment in that branch.");
    }

    static ApiException oneTimeTokenInvalid() {
        return new ApiException(
                HttpStatus.BAD_REQUEST, ErrorCode.ONE_TIME_TOKEN_INVALID, "The link is expired, used or unknown.");
    }
}
