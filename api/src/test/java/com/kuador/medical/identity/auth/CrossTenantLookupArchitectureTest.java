package com.kuador.medical.identity.auth;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ADR-012: the login lookup is the only read across clinics, and only
 * {@link AuthenticationService} may use it. Any other class that depends on it breaks the build.
 */
class CrossTenantLookupArchitectureTest {

    static final ArchRule ONLY_AUTHENTICATION_SERVICE_READS_ACROSS_CLINICS = noClasses()
            .that(DescribedPredicate.describe(
                    "are not AuthenticationService nor the lookup itself",
                    (JavaClass type) -> !isAuthenticationService(type) && !type.isAssignableTo(CrossTenantCredentialLookup.class)))
            .should().dependOnClassesThat().areAssignableTo(CrossTenantCredentialLookup.class)
            .because("ADR-012 allows a single read across clinics, encapsulated in the login");

    @Test
    void onlyTheAuthenticationServiceUsesTheCrossTenantLookup() {
        ONLY_AUTHENTICATION_SERVICE_READS_ACROSS_CLINICS.check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.kuador.medical"));
    }

    @Test
    void theRuleCatchesAnyOtherUser() {
        var result = ONLY_AUTHENTICATION_SERVICE_READS_ACROSS_CLINICS.evaluate(new ClassFileImporter()
                .importClasses(SneakyCredentialReader.class, CrossTenantCredentialLookup.class, AuthenticationService.class));

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .allSatisfy(detail -> assertThat(detail).contains("SneakyCredentialReader"));
    }

    private static boolean isAuthenticationService(JavaClass type) {
        String name = AuthenticationService.class.getName();
        return type.getName().equals(name) || type.getName().startsWith(name + "$");
    }

    /** Fixture: a class that must not read credentials across clinics. */
    static class SneakyCredentialReader {

        private final CrossTenantCredentialLookup lookup;

        SneakyCredentialReader(CrossTenantCredentialLookup lookup) {
            this.lookup = lookup;
        }

        List<StoredCredential> peek(String email) {
            return lookup.activeCredentials(email);
        }
    }
}
