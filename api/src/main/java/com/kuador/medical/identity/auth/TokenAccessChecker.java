package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.shared.security.AccessChecker;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** {@link AccessChecker} of the running application: the caller authenticated by its token. */
@Component
class TokenAccessChecker implements AccessChecker, CurrentUser {

    @Override
    public Optional<AuthenticatedAccount> current() {
        return RequestAccounts.current();
    }

    @Override
    public boolean isAuthenticated() {
        return current().isPresent();
    }

    @Override
    public boolean hasPermission(String permission) {
        return current().map(account -> account.hasPermission(permission)).orElse(false);
    }

    @Override
    public boolean isPasswordChangeRequired() {
        return current().map(AuthenticatedAccount::mustChangePassword).orElse(false);
    }
}
