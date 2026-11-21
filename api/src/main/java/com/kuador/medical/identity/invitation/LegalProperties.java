package com.kuador.medical.identity.invitation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code kuador.legal}: versions of the terms of service and of the data policy that the OWNER
 * accepts (FR-008). The texts are versioned content provided by Kuador [POR DEFINIR: texto final
 * de términos y política de datos]; publishing a new text means raising its version here.
 */
@ConfigurationProperties("kuador.legal")
public record LegalProperties(
        @DefaultValue("2026-11") String termsVersion, @DefaultValue("2026-11") String dataPolicyVersion) {}
