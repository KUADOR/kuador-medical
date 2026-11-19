package com.kuador.medical.shared.web;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into Problem Details (RFC 9457) extended with a stable {@code code}
 * and, for validation errors, the list of invalid fields. Never exposes internal messages.
 */
@RestControllerAdvice
class ProblemDetailsHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsHandler.class);

    /** The contract requires {@code type}; Spring omits it unless it is set explicitly. */
    private static final URI DEFAULT_TYPE = URI.create("about:blank");

    private static final Set<String> REQUIRED_CONSTRAINTS = Set.of("NotNull", "NotBlank", "NotEmpty");
    private static final Set<String> LENGTH_CONSTRAINTS = Set.of("Size", "Length");
    private static final Set<String> FORMAT_CONSTRAINTS = Set.of("Email", "Pattern");

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApiException(ApiException ex) {
        ProblemDetail problem = problem(ex.status(), ex.getMessage(), ex.code());
        if (!ex.errors().isEmpty()) {
            problem.setProperty("errors", ex.errors().stream()
                    .map(error -> Map.of("field", error.field(), "code", error.code()))
                    .toList());
        }
        HttpHeaders headers = new HttpHeaders();
        if (ex.status() == HttpStatus.UNAUTHORIZED) {
            headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return ResponseEntity.status(ex.status()).headers(headers).body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "The request is not valid.", ErrorCode.VALIDATION);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex) {
        // Only the exception type is logged here: messages may carry personal data.
        log.error("Unexpected error ({})", ex.getClass().getName(), ex);
        ProblemDetail problem = problem(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", ErrorCode.INTERNAL_ERROR);
        return ResponseEntity.internalServerError().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "The request is not valid.", ErrorCode.VALIDATION);
        problem.setProperty("errors", fieldErrors(ex.getBindingResult().getFieldErrors()));
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Framework errors (404, 405, 415, unreadable body…) keep their status and get a code. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail detail
                ? detail
                : ProblemDetail.forStatus(status);
        if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
            problem.setProperty("code", codeFor(status));
        }
        if (status.is5xxServerError()) {
            problem.setDetail("An unexpected error occurred.");
        }
        problem.setType(DEFAULT_TYPE);
        return super.handleExceptionInternal(ex, problem, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatusCode status, String detail, ErrorCode code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(DEFAULT_TYPE);
        problem.setProperty("code", code);
        return problem;
    }

    private static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            default -> status.is4xxClientError() ? ErrorCode.VALIDATION : ErrorCode.INTERNAL_ERROR;
        };
    }

    private static List<Map<String, String>> fieldErrors(List<FieldError> errors) {
        return errors.stream()
                .map(error -> Map.of("field", error.getField(), "code", fieldCode(error.getCode())))
                .toList();
    }

    private static String fieldCode(String constraint) {
        if (constraint == null) {
            return "INVALID";
        }
        if (REQUIRED_CONSTRAINTS.contains(constraint)) {
            return "REQUIRED";
        }
        if (LENGTH_CONSTRAINTS.contains(constraint)) {
            return "INVALID_LENGTH";
        }
        if (FORMAT_CONSTRAINTS.contains(constraint)) {
            return "INVALID_FORMAT";
        }
        return "INVALID";
    }
}
