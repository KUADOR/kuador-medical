package com.kuador.medical.identity;

import java.util.Optional;

/** The authenticated caller of the current request, if any. */
public interface CurrentUser {

    Optional<AuthenticatedAccount> current();
}
