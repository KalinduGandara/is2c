package com.is2c.hrm.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.springframework.data.jpa.repository.Query;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/** NFR-005 (DB portability) and NFR-018 (module boundaries). */
@AnalyzeClasses(packages = "com.is2c.hrm", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "com.is2c.hrm.";

    @ArchTest
    static final ArchRule noJdbcOutsidePlatform = noClasses()
            .that().resideOutsideOfPackage("com.is2c.hrm.platform..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..")
            .as("NFR-005 vendor SQL / JDBC access only inside the platform package");

    @ArchTest
    static final ArchRule noNativeQueries = methods()
            .that().areAnnotatedWith(Query.class)
            .should(new ArchCondition<>("not use native SQL") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    Query q = method.getAnnotationOfType(Query.class);
                    if (q.nativeQuery()) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " uses a native query (NFR-005)"));
                    }
                }
            })
            .allowEmptyShould(true)
            .as("NFR-005 repositories use JPQL only");

    @ArchTest
    static final ArchRule webLayerIsModulePrivate = classes()
            .that().resideInAPackage("com.is2c.hrm.*.web..")
            .should(new ArchCondition<>("only be accessed from its own module") {
                @Override
                public void check(JavaClass target, ConditionEvents events) {
                    String module = moduleOf(target);
                    target.getAccessesToSelf().forEach(access -> {
                        JavaClass origin = access.getOriginOwner();
                        String originModule = moduleOf(origin);
                        if (!module.equals(originModule) && !"common".equals(module)) {
                            events.add(SimpleConditionEvent.violated(access, access.getDescription()));
                        }
                    });
                }
            })
            .allowEmptyShould(true)
            .as("NFR-018 web layer (controllers/DTOs) is private to its module");

    @ArchTest
    static final ArchRule controllersAreSecured = classes()
            .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .and(DescribedPredicate.describe("not public auth/integration endpoints",
                    (JavaClass c) -> !c.getSimpleName().equals("AuthController")
                            && !c.getSimpleName().equals("AttendanceIngestionController")))
            .should().resideInAPackage("com.is2c.hrm..web..")
            .allowEmptyShould(true)
            .as("NFR-018 controllers live in a module web package");

    static String moduleOf(JavaClass c) {
        String name = c.getPackageName();
        if (!name.startsWith(BASE)) {
            return "";
        }
        String rest = name.substring(BASE.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
