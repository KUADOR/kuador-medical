package com.kuador.medical.platform.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /tenants/signup}. */
record TenantSignupRequest(
        @NotBlank @Size(max = 120) String clinicName,
        @NotBlank @Size(max = 120) String ownerDisplayName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 128) String password) {

    @Override
    public String toString() {
        return "TenantSignupRequest[redacted]";
    }
}
