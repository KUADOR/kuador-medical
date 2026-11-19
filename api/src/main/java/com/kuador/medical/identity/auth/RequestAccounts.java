package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.AuthenticatedAccount;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Keeps the authenticated caller as an attribute of the current HTTP request. */
final class RequestAccounts {

    private static final String ATTRIBUTE = RequestAccounts.class.getName() + ".account";

    private RequestAccounts() {}

    static void bind(HttpServletRequest request, AuthenticatedAccount account) {
        request.setAttribute(ATTRIBUTE, account);
    }

    static Optional<AuthenticatedAccount> current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST))
                .map(AuthenticatedAccount.class::cast);
    }
}
