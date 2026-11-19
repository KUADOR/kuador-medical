package com.kuador.medical.shared.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ADR-007: the tenant is applied centrally by Hibernate {@code @TenantId}; data of
 * tenant B is invisible and untouchable from tenant A.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TenantIsolationIT {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID tenantA;
    private UUID tenantB;
    private UUID probeOfB;

    @BeforeEach
    void createTwoTenants() {
        tenantA = insertTenant("Clinic A");
        tenantB = insertTenant("Clinic B");
        inTenant(tenantA, () -> persist(new TenantScopedProbe("role-of-a")));
        probeOfB = inTenant(tenantB, () -> persist(new TenantScopedProbe("role-of-b"))).getId();
    }

    @Test
    void entitiesTakeTheTenantOfTheCurrentContext() {
        UUID storedTenant = jdbc.queryForObject("select tenant_id from identity_role where id = ?", UUID.class, probeOfB);

        assertThat(storedTenant).isEqualTo(tenantB);
    }

    @Test
    void queriesOnlyReturnRowsOfTheCurrentTenant() {
        List<String> names = inTenant(tenantA, () -> entityManager
                .createQuery("select p.name from TenantScopedProbe p", String.class)
                .getResultList());

        assertThat(names).containsExactly("role-of-a");
    }

    @Test
    void rowOfAnotherTenantIsNotFoundById() {
        List<TenantScopedProbe> found = inTenant(tenantA, () -> entityManager
                .createQuery("select p from TenantScopedProbe p where p.id = :id", TenantScopedProbe.class)
                .setParameter("id", probeOfB)
                .getResultList());

        assertThat(found).isEmpty();
    }

    @Test
    void rowOfAnotherTenantCannotBeUpdatedOrDeleted() {
        int updated = inTenant(tenantA, () -> entityManager
                .createQuery("update TenantScopedProbe p set p.name = 'hijacked' where p.id = :id")
                .setParameter("id", probeOfB)
                .executeUpdate());
        int deleted = inTenant(tenantA, () -> entityManager
                .createQuery("delete from TenantScopedProbe p where p.id = :id")
                .setParameter("id", probeOfB)
                .executeUpdate());

        assertThat(updated).isZero();
        assertThat(deleted).isZero();
        assertThat(jdbc.queryForObject("select name from identity_role where id = ?", String.class, probeOfB))
                .isEqualTo("role-of-b");
    }

    @Test
    void entityCannotBeWrittenIntoAnotherTenant() {
        var probe = new TenantScopedProbe("smuggled");
        probe.forceTenantId(tenantB);

        assertThatThrownBy(() -> inTenant(tenantA, () -> persist(probe))).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("select count(*) from identity_role where name = 'smuggled'", Integer.class))
                .isZero();
    }

    @Test
    void withoutTenantNothingIsVisible() {
        List<String> names = transaction.execute(status -> entityManager
                .createQuery("select p.name from TenantScopedProbe p", String.class)
                .getResultList());

        assertThat(TenantContext.current()).isEmpty();
        assertThat(names).isEmpty();
    }

    @Test
    void withoutTenantNothingCanBeWritten() {
        assertThatThrownBy(() -> transaction.execute(status -> persist(new TenantScopedProbe("orphan"))))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("select count(*) from identity_role where name = 'orphan'", Integer.class))
                .isZero();
    }

    @Test
    void tenantContextIsOnlyBoundInsideItsScope() {
        UUID inside = TenantContext.callAs(tenantA, () -> TenantContext.current().orElseThrow());

        assertThat(inside).isEqualTo(tenantA);
        assertThat(TenantContext.current()).isEmpty();
    }

    private <T> T inTenant(UUID tenant, java.util.function.Supplier<T> work) {
        return TenantContext.callAs(tenant, () -> transaction.execute(status -> work.get()));
    }

    private TenantScopedProbe persist(TenantScopedProbe probe) {
        entityManager.persist(probe);
        entityManager.flush();
        return probe;
    }

    private UUID insertTenant(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into tenant (id, name, status) values (?, ?, 'ACTIVE')", id, name);
        return id;
    }
}
