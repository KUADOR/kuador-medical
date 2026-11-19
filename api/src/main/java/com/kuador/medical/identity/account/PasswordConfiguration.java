package com.kuador.medical.identity.account;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** ADR-006: Argon2id hashes, prefixed with their algorithm so they can be upgraded later. */
@Configuration(proxyBeanMethods = false)
class PasswordConfiguration {

    private static final String ARGON2 = "argon2";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder(
                ARGON2, Map.of(ARGON2, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }
}
