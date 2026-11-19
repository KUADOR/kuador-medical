package com.kuador.medical.identity.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /** {@code email} must already be normalized ({@link EmailAddresses#normalize}). */
    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPlatformAdminTrue();
}
