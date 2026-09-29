package rw.itunda.loansservice

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.Configuration
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.data.repository.Repository
import org.springframework.stereotype.Component

// Same real gap this closes as card-service's own WiringTest.kt (see
// project_itunda_extracted_services_no_wiring_tests): a real
// UnsatisfiedDependencyException at boot is only ever caught today by an actual
// live deploy attempt, unless this test catches it first. Deliberately NOT a
// @SpringBootTest -- see CardServiceApplication's own WiringTest.kt doc comment
// for the full reasoning this file copies exactly (read directly off the real
// LoansServiceApplication annotation, not a hand-copied duplicate that could
// silently drift).
class WiringTest {

    private val componentScan = LoansServiceApplication::class.java.getAnnotation(org.springframework.context.annotation.ComponentScan::class.java)
    private val basePackages = componentScan.basePackages.toList()
    private val excludedClassNames = componentScan.excludeFilters
        .flatMap { it.classes.map { cls -> cls.java.name } }
        .toSet()

    private fun scanBeanClassNames(packages: List<String>): Set<String> {
        val provider = ClassPathScanningCandidateComponentProvider(false)
        provider.addIncludeFilter(AnnotationTypeFilter(Component::class.java))
        provider.addIncludeFilter(AnnotationTypeFilter(Configuration::class.java))
        return packages.flatMap { provider.findCandidateComponents(it) }.map { it.beanClassName!! }.toSet()
    }

    private fun beanFactoryMethodReturnTypes(configClassNames: Set<String>): Set<Class<*>> =
        configClassNames.flatMap { className ->
            Class.forName(className).declaredMethods.filter { it.isAnnotationPresent(Bean::class.java) }.map { it.returnType }
        }.toSet()

    @Test
    fun `every reachable bean's rw-itunda constructor dependencies are satisfiable within this service's own real scanBasePackages and excludeFilters`() {
        val scanned = scanBeanClassNames(basePackages)
        val reachable = scanned - excludedClassNames

        val allBeansOnClasspath = scanBeanClassNames(listOf("rw.itunda"))

        val reachableBeanFactoryTypes = beanFactoryMethodReturnTypes(reachable)
        val anyBeanFactoryTypes = beanFactoryMethodReturnTypes(allBeansOnClasspath)

        val failures = mutableListOf<String>()

        for (beanClassName in reachable) {
            val beanClass = Class.forName(beanClassName)
            val constructor = beanClass.declaredConstructors.maxByOrNull { it.parameterCount } ?: continue
            for (paramType in constructor.parameterTypes) {
                val paramName = paramType.name
                if (!paramName.startsWith("rw.itunda.")) continue
                if (Repository::class.java.isAssignableFrom(paramType)) continue

                if (paramType.isInterface || java.lang.reflect.Modifier.isAbstract(paramType.modifiers)) {
                    val implementors = allBeansOnClasspath.filter { candidateName -> paramType.isAssignableFrom(Class.forName(candidateName)) }
                    val hasAnyFactoryProvider = anyBeanFactoryTypes.any { paramType.isAssignableFrom(it) }
                    if (implementors.isEmpty() && !hasAnyFactoryProvider) {
                        failures += "$beanClassName needs $paramName (an interface/abstract type), but NO real implementation or @Bean provider exists anywhere on this service's classpath"
                        continue
                    }
                    val reachableViaFactory = reachableBeanFactoryTypes.any { paramType.isAssignableFrom(it) }
                    if (implementors.none { it in reachable } && !reachableViaFactory) {
                        failures += "$beanClassName needs $paramName -- a real implementation/@Bean provider exists (${implementors.joinToString()}) but none are reachable via this service's own scanBasePackages/excludeFilters"
                    }
                } else {
                    val isSpringBean = allBeansOnClasspath.contains(paramName) || anyBeanFactoryTypes.any { it == paramType }
                    val isReachable = paramName in reachable || reachableBeanFactoryTypes.any { it == paramType }
                    if (isSpringBean && !isReachable) {
                        failures += "$beanClassName needs $paramName -- it's a real Spring bean on this classpath but not reachable via this service's own scanBasePackages/excludeFilters"
                    }
                }
            }
        }

        assertTrue(failures.isEmpty(), "Found ${failures.size} unsatisfiable constructor dependency(s) in loans-service's own real wiring:\n" + failures.joinToString("\n"))
    }

    // Real gap this closes (2026-09-14): IdempotencyExceptionHandler lived in
    // rw.itunda.app.web, a package this service's own scanBasePackages never
    // included -- the repo's only @RestControllerAdvice was silently never
    // registered here, turning a malformed Idempotency-Key header or a real
    // ObjectOptimisticLockingFailureException into a raw 500 instead of the
    // intended clean 400/409. Moved to rw.itunda.core.web (already scanned) --
    // this test proves it's actually reachable via this service's own real
    // scanBasePackages, not just present somewhere on the classpath.
    @Test
    fun `the repo's global IdempotencyExceptionHandler is actually reachable via this service's own real scanBasePackages`() {
        val scanned = scanBeanClassNames(basePackages)
        assertTrue(
            scanned.contains("rw.itunda.core.web.IdempotencyExceptionHandler"),
            "IdempotencyExceptionHandler is not reachable via this service's own scanBasePackages ($basePackages) -- a malformed Idempotency-Key header or a real optimistic-lock conflict would surface as a raw 500 instead of a clean 400/409",
        )
    }

    // Real gap this closes (2026-09-14): RequestCorrelationFilter -- the ONLY place in
    // the whole backend that ever calls MDC.put -- lived in rw.itunda.app.observability,
    // a package this service's own scanBasePackages never included -- same exact bug
    // class as the IdempotencyExceptionHandler fix above. Every service's own
    // application.yml logging pattern expects a real requestId in the MDC; without this
    // filter registered, every log line this service emits prints a permanently-empty
    // [requestId=], and the X-Request-ID response header is never set either. Moved to
    // rw.itunda.core.observability (already scanned) -- this test proves it's actually
    // reachable via this service's own real scanBasePackages.
    @Test
    fun `the repo's only log-correlation MDC filter is actually reachable via this service's own real scanBasePackages`() {
        val scanned = scanBeanClassNames(basePackages)
        assertTrue(
            scanned.contains("rw.itunda.core.observability.RequestCorrelationFilter"),
            "RequestCorrelationFilter is not reachable via this service's own scanBasePackages ($basePackages) -- every log line this service emits would print a permanently-empty [requestId=] and X-Request-ID would never be set",
        )
    }
}
