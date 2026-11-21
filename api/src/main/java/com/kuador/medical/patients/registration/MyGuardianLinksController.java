package com.kuador.medical.patients.registration;

import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.patients.registration.Responses.GuardianLinkResponse;
import com.kuador.medical.shared.security.AuthenticatedEndpoint;
import com.kuador.medical.shared.security.RequiresModule;
import com.kuador.medical.shared.web.ApiException;
import jakarta.validation.Valid;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code /me/guardian-links}: links of the adult patient behind the calling account (MEDK-18). */
@RestController
@RequestMapping("/me/guardian-links")
@RequiresModule("PATIENTS")
class MyGuardianLinksController {

    private final MyGuardianLinkService links;
    private final CurrentUser currentUser;

    MyGuardianLinksController(MyGuardianLinkService links, CurrentUser currentUser) {
        this.links = links;
        this.currentUser = currentUser;
    }

    @AuthenticatedEndpoint
    @GetMapping
    List<GuardianLinkResponse> list() {
        return links.list(caller());
    }

    @AuthenticatedEndpoint
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GuardianLinkResponse authorize(
            @RequestHeader(name = "Idempotency-Key", required = false) @Nullable String idempotencyKey,
            @Valid @RequestBody AuthorizeLinkRequest request) {
        return links.authorize(request, idempotencyKey, caller());
    }

    private AuthenticatedAccount caller() {
        return currentUser.current().orElseThrow(ApiException::unauthenticated);
    }
}
