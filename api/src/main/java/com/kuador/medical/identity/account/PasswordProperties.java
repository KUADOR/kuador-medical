package com.kuador.medical.identity.account;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Minimum password policy ({@code kuador.password}); configurable, 12 characters by default. */
@ConfigurationProperties("kuador.password")
public record PasswordProperties(@DefaultValue("12") int minLength) {}
