package com.kuador.medical.platform.tenant;

import com.kuador.medical.shared.security.PublicEndpoint;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /tenants/signup}: whoever subscribes creates the clinic and becomes OWNER and ADMIN. */
@RestController
@RequestMapping("/tenants")
class TenantSignupController {

    private final TenantSignupService signups;

    TenantSignupController(TenantSignupService signups) {
        this.signups = signups;
    }

    @PublicEndpoint
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    TenantSignupResponse signUp(@Valid @RequestBody TenantSignupRequest request) {
        return new TenantSignupResponse(signups.signUp(request));
    }
}
