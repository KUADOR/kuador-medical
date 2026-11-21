package com.kuador.medical;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.platform.plan.ModuleCode;
import com.kuador.medical.shared.security.AllowedWhenReadOnly;
import com.kuador.medical.shared.security.PublicEndpoint;
import com.kuador.medical.shared.security.RequiresModule;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * Build-breaking rules of the plan capabilities and the read-only mode (ADR-017, MEDK-69), next to
 * {@link ArchitectureTests} and {@link ModularityTests}.
 */
class TenantAccessArchitectureTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.kuador.medical");

    /**
     * Writes of a clinic session that still answer while the clinic is read-only. Adding one is a
     * decision: it must keep the clinic able only to sign in, read and export (ADR-017).
     */
    private static final Set<String> ALLOWED_WHEN_READ_ONLY = Set.of(
            "AuthController.switchBranch",
            "AuthController.changePassword",
            "TwoFactorController.start",
            "TwoFactorController.confirm",
            "TwoFactorController.verify");

    @Test
    void everyRequiredModuleBelongsToTheCatalog() {
        Set<String> catalog = Arrays.stream(ModuleCode.values()).map(Enum::name).collect(Collectors.toSet());
        List<String> declared = Stream.concat(
                        controllers().map(type -> type.tryGetAnnotationOfType(RequiresModule.class)),
                        controllers().flatMap(type -> type.getMethods().stream())
                                .map(method -> method.tryGetAnnotationOfType(RequiresModule.class)))
                .flatMap(java.util.Optional::stream)
                .map(RequiresModule::value)
                .toList();

        assertThat(declared).as("@RequiresModule is used").isNotEmpty();
        assertThat(declared).allSatisfy(module -> assertThat(catalog).as("ModuleCode").contains(module));
    }

    @Test
    void onlyTheReviewedWritesAnswerWhileTheClinicIsReadOnly() {
        Set<String> exempted = controllers()
                .flatMap(type -> type.getMethods().stream())
                .filter(TenantAccessArchitectureTest::isWriteHandler)
                .filter(method -> !isMarked(method, PublicEndpoint.class))
                .filter(method -> isMarked(method, AllowedWhenReadOnly.class))
                .map(method -> method.getOwner().getSimpleName() + "." + method.getName())
                .collect(Collectors.toSet());

        assertThat(exempted).containsExactlyInAnyOrderElementsOf(ALLOWED_WHEN_READ_ONLY);
    }

    @Test
    void readHandlersDoNotNeedTheReadOnlyExemption() {
        assertThat(controllers()
                .flatMap(type -> type.getMethods().stream())
                .filter(method -> method.isAnnotatedWith(GetMapping.class))
                .filter(method -> method.isAnnotatedWith(AllowedWhenReadOnly.class))
                .map(JavaMethod::getFullName))
                .as("reads always answer in READ_ONLY and RETENTION")
                .isEmpty();
    }

    private static Stream<JavaClass> controllers() {
        return PRODUCTION_CLASSES.stream().filter(type -> type.isMetaAnnotatedWith(Controller.class));
    }

    private static boolean isWriteHandler(JavaMethod method) {
        return method.isAnnotatedWith(PostMapping.class) || method.isAnnotatedWith(PutMapping.class)
                || method.isAnnotatedWith(PatchMapping.class) || method.isAnnotatedWith(DeleteMapping.class);
    }

    private static boolean isMarked(JavaMethod method, Class<? extends java.lang.annotation.Annotation> marker) {
        return method.isAnnotatedWith(marker) || method.getOwner().isAnnotatedWith(marker);
    }
}
