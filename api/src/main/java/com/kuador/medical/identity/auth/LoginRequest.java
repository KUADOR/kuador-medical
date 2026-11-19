package com.kuador.medical.identity.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record LoginRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {

    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
