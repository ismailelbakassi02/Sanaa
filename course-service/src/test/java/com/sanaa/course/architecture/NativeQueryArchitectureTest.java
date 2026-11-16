package com.sanaa.course.architecture;

import jakarta.persistence.EntityManager;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * Guards the multi-tenant safety rule: Hibernate's {@code @TenantId} filter only applies to HQL and
 * criteria queries, so native SQL on a tenant-owned table would silently read or write other
 * tenants' rows.
 *
 * <p>Native SQL is allowed only in the infrastructure packages that are intentionally global: the
 * public and learner read models ({@code crosstenant}), which span publishers by design, and the
 * event package, whose idempotency ledger and placeholder snapshots are not tenant-owned.
 *
 * <p>The public catalogue must also not depend on the tenant-scoped repositories at all, otherwise a
 * later refactor could reintroduce a filtered read behind an anonymous page.
 */
@AnalyzeClasses(packages = "com.sanaa.course")
class NativeQueryArchitectureTest {

    private static final String[] ALLOWED_PACKAGES = {
            "com.sanaa.course.crosstenant..",
            "com.sanaa.course.event.."
    };

    /** A native {@code @Query} bypasses the tenant discriminator, so it must stay global. */
    @ArchTest
    static final ArchRule native_queries_stay_in_infrastructure_packages = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage("com.sanaa.course..")
            .and()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackages(ALLOWED_PACKAGES)
            .should(notDeclareANativeQuery())
            .because("native SQL skips Hibernate's @TenantId filter");

    @ArchTest
    static final ArchRule entity_manager_native_queries_stay_in_infrastructure_packages = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage("com.sanaa.course..")
            .and()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackages(ALLOWED_PACKAGES)
            .should(notCallCreateNativeQuery())
            .because("native SQL skips Hibernate's @TenantId filter");

    @ArchTest
    static final ArchRule jdbc_templates_stay_in_infrastructure_packages = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage("com.sanaa.course..")
            .and()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackages(ALLOWED_PACKAGES)
            .should(notCallAJdbcTemplate())
            .because("JdbcTemplate statements bypass Hibernate's @TenantId filter");

    @ArchTest
    static final ArchRule the_public_catalogue_never_touches_tenant_scoped_repositories =
            noClasses()
                    .that()
                    .resideInAPackage("com.sanaa.course.web.publicapi..")
                    .or()
                    .haveFullyQualifiedName("com.sanaa.course.service.PublicCatalogueService")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.sanaa.course.repository..")
                    .because("an anonymous page must read the cross-tenant projection, never one"
                            + " publisher's tenant-filtered repository");

    private static ArchCondition<JavaMethod> notDeclareANativeQuery() {
        return new ArchCondition<>("not declare a native @Query") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (method.isAnnotatedWith(org.springframework.data.jpa.repository.Query.class)
                        && method.getAnnotationOfType(
                                org.springframework.data.jpa.repository.Query.class).nativeQuery()) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " declares a native @Query"));
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> notCallCreateNativeQuery() {
        return new ArchCondition<>("not call EntityManager.createNativeQuery") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
                    if (call.getTarget().getName().equals("createNativeQuery")
                            && call.getTarget().getOwner().isEquivalentTo(EntityManager.class)) {
                        events.add(SimpleConditionEvent.violated(call,
                                call.getDescription() + " calls EntityManager.createNativeQuery"));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> notCallAJdbcTemplate() {
        return new ArchCondition<>("not call a JdbcTemplate statement") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
                    if (call.getTarget().getOwner().isEquivalentTo(JdbcTemplate.class)
                            || call.getTarget().getOwner()
                                    .isEquivalentTo(NamedParameterJdbcTemplate.class)) {
                        events.add(SimpleConditionEvent.violated(call,
                                call.getDescription() + " calls a JdbcTemplate statement"));
                    }
                }
            }
        };
    }
}
