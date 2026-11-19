package com.kuador.medical;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.shared.security.AuthenticatedEndpoint;
import com.kuador.medical.shared.security.PublicEndpoint;
import com.kuador.medical.shared.security.RequiresPermission;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.annotation.Annotation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Build-breaking architecture rules (ADR-008).
 */
class ArchitectureTests {

    private static final List<Class<? extends Annotation>> ACCESS_ANNOTATIONS =
            List.of(PublicEndpoint.class, AuthenticatedEndpoint.class, RequiresPermission.class);

    /** Every controller handler declares exactly one access level, on the method or on its class. */
    static final ArchRule EVERY_ENDPOINT_DECLARES_ITS_ACCESS = methods()
            .that(areRequestHandlers())
            .should(declareExactlyOneAccessLevel())
            .allowEmptyShould(true);


    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.kuador.medical");

    @Test
    void everyEndpointDeclaresItsAccess() {
        EVERY_ENDPOINT_DECLARES_ITS_ACCESS.check(PRODUCTION_CLASSES);
    }


    @Test
    void accessRuleFailsForAControllerWithoutDeclaration() {
        var result = EVERY_ENDPOINT_DECLARES_ITS_ACCESS.evaluate(
                new ClassFileImporter().importClasses(UndeclaredController.class));

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .singleElement()
                .asString()
                .contains("UndeclaredController.list()");
    }

    @Test
    void accessRuleFailsForAHandlerDeclaringTwoAccessLevels() {
        var result = EVERY_ENDPOINT_DECLARES_ITS_ACCESS.evaluate(
                new ClassFileImporter().importClasses(AmbiguousController.class));

        assertThat(result.hasViolation()).isTrue();
    }

    @Test
    void accessRulePassesForDeclaredControllers() {
        var result = EVERY_ENDPOINT_DECLARES_ITS_ACCESS.evaluate(
                new ClassFileImporter().importClasses(DeclaredController.class, ClassLevelController.class));

        assertThat(result.hasViolation()).isFalse();
    }

    private static DescribedPredicate<JavaMethod> areRequestHandlers() {
        return DescribedPredicate.describe(
                "are request handlers of a controller",
                method -> isAnnotatedWith(method.getOwner(), Controller.class)
                        && isAnnotatedWith(method, RequestMapping.class));
    }

    private static ArchCondition<JavaMethod> declareExactlyOneAccessLevel() {
        return new ArchCondition<>("declare exactly one of @PublicEndpoint, @AuthenticatedEndpoint or @RequiresPermission") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                long onMethod = countAccessAnnotations(method);
                long onClass = countAccessAnnotations(method.getOwner());
                boolean satisfied = onMethod == 1 || (onMethod == 0 && onClass == 1);
                String message = "%s declares %d access annotation(s) on the method and %d on the class"
                        .formatted(method.getFullName(), onMethod, onClass);
                events.add(new SimpleConditionEvent(method, satisfied, message));
            }
        };
    }

    private static long countAccessAnnotations(CanBeAnnotated element) {
        return ACCESS_ANNOTATIONS.stream().filter(element::isAnnotatedWith).count();
    }

    private static boolean isAnnotatedWith(CanBeAnnotated element, Class<? extends Annotation> annotation) {
        return element.isAnnotatedWith(annotation) || element.isMetaAnnotatedWith(annotation);
    }

    @RestController
    static class UndeclaredController {

        @GetMapping("/fixture")
        String list() {
            return "";
        }
    }

    @RestController
    static class AmbiguousController {

        @PublicEndpoint
        @RequiresPermission("roles.read")
        @GetMapping("/fixture")
        String list() {
            return "";
        }
    }

    @RestController
    static class DeclaredController {

        @PublicEndpoint
        @GetMapping("/public")
        String open() {
            return "";
        }

        @AuthenticatedEndpoint
        @GetMapping("/me")
        String me() {
            return "";
        }

        @RequiresPermission("roles.read")
        @GetMapping("/roles")
        String roles() {
            return "";
        }

        String notAHandler() {
            return "";
        }
    }

    @RestController
    @RequiresPermission("roles.read")
    static class ClassLevelController {

        @GetMapping("/roles")
        String list() {
            return "";
        }
    }
}
