package com.kuador.medical.shared.web;

import java.time.Duration;
import org.springframework.http.HttpStatus;

/** 429 {@code TOO_MANY_ATTEMPTS}, answered with a {@code Retry-After} header (seconds). */
public class TooManyAttemptsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_ATTEMPTS, "Too many attempts. Try again later.");
        this.retryAfterSeconds = Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
