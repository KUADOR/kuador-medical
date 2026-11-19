package com.kuador.medical.identity.bootstrap;

import com.kuador.medical.identity.account.EmailAddresses;
import com.kuador.medical.identity.account.UserAccount;
import com.kuador.medical.identity.account.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the initial SUPER_ADMIN account at startup (FR-013), idempotently: only when no
 * platform admin exists, with a password that must be changed on first login. Logs never
 * contain the email nor the password.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    static final String DISPLAY_NAME = "Kuador";

    private final BootstrapAdminProperties properties;
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(
            BootstrapAdminProperties properties, UserAccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing();
    }

    /** Each repository call runs in its own transaction; the unique email settles concurrent starts. */
    public void createIfMissing() {
        if (accounts.existsByPlatformAdminTrue()) {
            log.info("A SUPER_ADMIN account already exists; the initial account is not created.");
            return;
        }
        if (!properties.isComplete()) {
            log.warn("No SUPER_ADMIN account exists and KUADOR_BOOTSTRAP_ADMIN_EMAIL or "
                    + "KUADOR_BOOTSTRAP_ADMIN_PASSWORD is not set; the initial account is not created.");
            return;
        }
        String email = EmailAddresses.normalize(properties.email());
        if (accounts.existsByEmail(email)) {
            log.warn("KUADOR_BOOTSTRAP_ADMIN_EMAIL belongs to a clinic account; the initial SUPER_ADMIN account is not created.");
            return;
        }
        try {
            accounts.saveAndFlush(UserAccount.initialPlatformAdmin(
                    email, passwordEncoder.encode(properties.password()), DISPLAY_NAME));
            log.info("Initial SUPER_ADMIN account created; its password must be changed on first login.");
        } catch (DataIntegrityViolationException e) {
            log.warn("The initial SUPER_ADMIN account was created concurrently by another instance.");
        }
    }
}
