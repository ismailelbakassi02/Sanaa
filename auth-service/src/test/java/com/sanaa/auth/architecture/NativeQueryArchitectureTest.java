package com.sanaa.auth.architecture;

import jakarta.persistence.EntityManager;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * Guards the multi-tenant safety rule from the architecture document: Hibernate's tenant filter only
 * applies to HQL and criteria queries, so a native query on a tenant-owned table would silently read
 * or write other tenants' rows.
 *
 * <p>Native SQL is allowed only in the infrastructure packages that are intentionally global:
 * the outbox poller (locks rows across tenants, then publishes the tenant id from the event), the
 * cross-tenant read views used by the public pages, and processed-event bookkeeping. Anything else
 * must go through {@code @TenantId} entities.
 */
@AnalyzeClasses(packages = "com.sanaa.auth")
class NativeQueryArchitectureTest {

    private static final String[] ALLOWED_PACKAGES = {
            "com.sanaa.auth.outbox..",
            "com.sanaa.auth.crosstenant..",
            "com.sanaa.auth.event.."
    };

    /**
     * A native {@code @Query} bypasses Hibernate's tenant discriminator, so it must not be declared
     * outside the infrastructure packages.
     */
    @ArchTest
    static final ArchRule native_queries_stay_in_infrastructure_packages = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage("com.sanaa.auth..")
            .and()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackages(ALLOWED_PACKAGES)
            .should(notDeclareANativeQuery())
            .because("native SQL skips Hibernate's @TenantId filter");

    /**
     * The same rule for code that builds a native query directly from an {@link EntityManager}.
     */
    @ArchTest
    static final ArchRule entity_manager_native_queries_stay_in_infrastructure_packages = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideInAPackage("com.sanaa.auth..")
            .and()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackages(ALLOWED_PACKAGES)
            .should(notCallCreateNativeQuery())
            .because("native SQL skips Hibernate's @TenantId filter");

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
                            && call.getTarget().getOwner().isEquivalentTo(
                                    EntityManager.class)) {
                        events.add(SimpleConditionEvent.violated(call,
                                call.getDescription() + " calls EntityManager.createNativeQuery"));
                    }
                }
            }
        };
    }
}
