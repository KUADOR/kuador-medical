package com.kuador.medical.identity.account;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Component;

/**
 * Stores new clinic accounts. The database decides races: a duplicated email in the clinic is
 * {@code 409 EMAIL_TAKEN} and an id already used (client-generated ids) is {@code 409 RESOURCE_ID_TAKEN}.
 */
@Component
public class ClinicAccountStore {

    private static final String EMAIL_UNIQUE = "identity_user_account_email_unique";
    private static final String PRIMARY_KEY = "identity_user_account_pkey";

    private final EntityManager entityManager;
    private final ClinicAccountRepository accounts;

    ClinicAccountStore(EntityManager entityManager, ClinicAccountRepository accounts) {
        this.entityManager = entityManager;
        this.accounts = accounts;
    }

    /** Persists and flushes a new account of the current clinic. */
    public void persistNew(ClinicAccount account) {
        if (accounts.existsByEmailInCurrentTenant(account.getEmail())) {
            throw emailTaken();
        }
        entityManager.persist(account);
        try {
            entityManager.flush();
        } catch (PersistenceException e) {
            if (e instanceof ConstraintViolationException violation) {
                if (EMAIL_UNIQUE.equals(violation.getConstraintName())) {
                    throw emailTaken();
                }
                if (PRIMARY_KEY.equals(violation.getConstraintName())) {
                    throw ApiException.conflict(ErrorCode.RESOURCE_ID_TAKEN, "The id is already used by another resource.");
                }
            }
            throw e;
        }
    }

    public static ApiException emailTaken() {
        return ApiException.conflict(ErrorCode.EMAIL_TAKEN, "The email is already registered in the clinic.");
    }
}
