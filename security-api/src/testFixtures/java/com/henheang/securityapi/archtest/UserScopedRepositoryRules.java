package com.henheang.securityapi.archtest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Enforces "every repository method is scoped by userId" for modules whose rows each belong to one
 * user (hub-api, finance-api). It used to be a written rule only; one unscoped finder would let one
 * user read or delete another user's rows - for H-Vault, real credentials.
 *
 * <p>Two rules, both checked against compiled classes:
 *
 * <ol>
 *   <li>{@link #noUnscopedInheritedCalls}: nothing outside the repository package calls a method
 *       inherited from Spring Data that ignores the owner - {@code findById}, {@code findAll},
 *       {@code count}, {@code deleteById}, ... ({@link #UNSCOPED_INHERITED}). {@code save} and
 *       {@code delete(entity)} are allowed: they act on an entity the service already loaded
 *       through a scoped finder, or built with the caller's userId.
 *   <li>{@link #declaredMethodsAreScoped}: every method a repository type declares takes a {@code
 *       UUID userId} parameter, and actually uses it - a {@code @Query} must reference {@code
 *       :userId}, a derived query's name must contain {@code UserId}.
 * </ol>
 *
 * <p>Exemptions are explicit strings and each one should carry a comment saying why it is safe;
 * adding one is a reviewed decision, not a way to make the build pass. Keys:
 *
 * <ul>
 *   <li>rule 1: {@code "CallerSimpleName -> RepositorySimpleName.method"}; for a method reference
 *       it is the declaring Spring Data interface, e.g. {@code "Caller -> CrudRepository.count"}
 *   <li>rule 2: {@code "RepositorySimpleName.method"}
 * </ul>
 *
 * <p>Known limitations: this is a structural check, not proof. A query that takes {@code :userId}
 * but ORs it away ({@code user_id = :userId OR true}) passes; reviews and the other-user-id
 * integration tests still matter. Reflection and native SQL outside the repository package aren't
 * seen. Rule 1 flags an unscoped method reference to <em>any</em> repository (javac doesn't record
 * which one), so referencing, say, security-api's {@code UserRepository::findById} needs an
 * exemption too.
 */
public final class UserScopedRepositoryRules {

    /** Spring Data methods that take no owner, so would see or touch every user's rows. */
    public static final Set<String> UNSCOPED_INHERITED =
            Set.of(
                    "findAll",
                    "findAllById",
                    "findById",
                    "findOne",
                    "findBy",
                    "getById",
                    "getOne",
                    "getReferenceById",
                    "existsById",
                    "exists",
                    "count",
                    "deleteById",
                    "deleteAllById",
                    "deleteAllByIdInBatch",
                    "deleteAll",
                    "deleteAllInBatch");

    private static final String SPRING_DATA = "org.springframework.data";

    private UserScopedRepositoryRules() {}

    /**
     * Rule 1.
     *
     * @param repositoryPackage the module's repository package, e.g. {@code
     *     com.henheang.hubapi.repository}
     */
    public static ArchRule noUnscopedInheritedCalls(
            String repositoryPackage, Set<String> exemptions) {
        return classes()
                .that()
                .resideOutsideOfPackage(repositoryPackage + "..")
                .should(
                        new ArchCondition<JavaClass>(
                                "not call Spring Data methods that ignore the owner on "
                                        + repositoryPackage
                                        + " repositories") {
                            @Override
                            public void check(JavaClass caller, ConditionEvents events) {
                                // Calls and method references (repo::findAll) are separate
                                // in ArchUnit; getAccessesFromSelf() misses references.
                                List<JavaCodeUnitAccess<?>> accesses = new ArrayList<>();
                                accesses.addAll(caller.getMethodCallsFromSelf());
                                accesses.addAll(caller.getMethodReferencesFromSelf());
                                for (JavaCodeUnitAccess<?> access : accesses) {
                                    checkCall(
                                            caller, access, repositoryPackage, exemptions, events);
                                }
                            }
                        })
                .because(
                        "every read or delete of user-owned rows must be scoped by the caller's"
                                + " userId (findByIdAndUserId, ...), never by id alone");
    }

    private static void checkCall(
            JavaClass caller,
            JavaCodeUnitAccess<?> call,
            String repositoryPackage,
            Set<String> exemptions,
            ConditionEvents events) {
        JavaClass owner = call.getTargetOwner();
        String method = call.getTarget().getName();
        if (!UNSCOPED_INHERITED.contains(method)) {
            return;
        }
        boolean moduleRepository =
                inPackage(owner, repositoryPackage) && owner.isAssignableTo(Repository.class);
        // javac compiles a method reference (repo::count) - and a call through a
        // JpaRepository-typed variable - against the Spring Data interface that declares
        // the method, e.g. CrudRepository.count, so which repository it hits is lost.
        // Flag those too: the module has no reason to go through the base types.
        boolean springDataBase = inPackage(owner, SPRING_DATA);
        if (!moduleRepository && !springDataBase) {
            return;
        }
        String key = topLevelSimpleName(caller) + " -> " + owner.getSimpleName() + "." + method;
        if (exemptions.contains(key)) {
            return;
        }
        events.add(
                SimpleConditionEvent.violated(
                        call, "unscoped call [" + key + "] in " + call.getDescription()));
    }

    /** Rule 2. */
    public static ArchRule declaredMethodsAreScoped(
            String repositoryPackage, Set<String> exemptions) {
        return classes()
                .that()
                .resideInAPackage(repositoryPackage)
                .and(REPOSITORY_TYPE)
                .should(
                        new ArchCondition<JavaClass>(
                                "declare only methods that take and use a UUID userId") {
                            @Override
                            public void check(JavaClass type, ConditionEvents events) {
                                for (JavaMethod method : type.getMethods()) {
                                    checkDeclared(type, method, exemptions, events);
                                }
                            }
                        })
                .because("a repository method without the owner's id can reach other users' rows");
    }

    private static void checkDeclared(
            JavaClass type, JavaMethod method, Set<String> exemptions, ConditionEvents events) {
        Set<JavaModifier> modifiers = method.getModifiers();
        if (modifiers.contains(JavaModifier.STATIC)
                || modifiers.contains(JavaModifier.PRIVATE)
                || modifiers.contains(JavaModifier.SYNTHETIC)
                || modifiers.contains(JavaModifier.BRIDGE)) {
            return;
        }
        String key = type.getSimpleName() + "." + method.getName();
        if (exemptions.contains(key)) {
            return;
        }
        Method reflected = method.reflect();
        if (!takesUserId(reflected)) {
            events.add(
                    SimpleConditionEvent.violated(
                            method,
                            key + " has no UUID userId parameter: " + method.getFullName()));
            return;
        }
        if (!type.isAssignableTo(Repository.class)) {
            return; // a custom fragment or JDBC repository: its SQL is code, reviewed by hand
        }
        Query query = reflected.getAnnotation(Query.class);
        if (query != null) {
            if (!query.value().contains(":userId")) {
                events.add(
                        SimpleConditionEvent.violated(
                                method, key + " takes userId but its @Query never uses :userId"));
            }
        } else if (!method.getName().contains("UserId") && !reflected.isDefault()) {
            events.add(
                    SimpleConditionEvent.violated(
                            method,
                            key + " takes userId but its derived query doesn't filter on UserId"));
        }
    }

    private static boolean takesUserId(Method method) {
        for (Parameter parameter : method.getParameters()) {
            if (parameter.getType() != UUID.class) {
                continue;
            }
            Param param = parameter.getAnnotation(Param.class);
            // Parameter names need -parameters, which the Spring Boot Gradle plugin sets.
            if ("userId".equals(parameter.getName())
                    || (param != null && "userId".equals(param.value()))) {
                return true;
            }
        }
        return false;
    }

    /** Spring Data interfaces, custom fragment interfaces/impls, and JDBC @Repository classes. */
    private static final DescribedPredicate<JavaClass> REPOSITORY_TYPE =
            new DescribedPredicate<>("are repository types") {
                @Override
                public boolean test(JavaClass type) {
                    return type.isInterface()
                            || type.isAnnotatedWith(org.springframework.stereotype.Repository.class)
                            || type.getSimpleName().endsWith("Impl");
                }
            };

    private static boolean inPackage(JavaClass type, String pkg) {
        return type.getPackageName().equals(pkg) || type.getPackageName().startsWith(pkg + ".");
    }

    private static String topLevelSimpleName(JavaClass type) {
        String name = type.getName();
        String simple = name.substring(name.lastIndexOf('.') + 1);
        int nested = simple.indexOf('$');
        return nested < 0 ? simple : simple.substring(0, nested);
    }
}
