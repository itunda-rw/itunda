package rw.itunda.billsservice

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.Configuration
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.data.repository.Repository
import org.springframework.stereotype.Component

// Real gap this closes -- see project_itunda_extracted_services_no_wiring_tests: none
// of the 13 independently-deployable *-service modules had ANY test of their own
// concern (the narrow @ComponentScan/excludeFilters wiring, not the business logic
// already covered by :card's/:insurance's/etc. own Kotest suite). A real
// UnsatisfiedDependencyException at boot (2026-09-01, in card-service --
// see CardServiceApplication.kt's own doc comment for the full account; this
// service shares the identical @ComponentScan/excludeFilters shape and was
// never itself proven to boot cleanly by anything other than a live deploy).
//
// This is deliberately NOT a @SpringBootTest -- this backend has zero
// Testcontainers/live-DB test infrastructure today, and adding the first one is a
// real, undecided, repo-wide decision (see the memory file above), not something to
// introduce unilaterally for just this gap. Instead: a plain reflection-based check,
// using Spring's own ClassPathScanningCandidateComponentProvider purely as a scanner
// (never boots an ApplicationContext, touches no database) to reproduce
// BillsServiceApplication's exact real @ComponentScan/excludeFilters configuration and
// verify every reachable bean's constructor dependencies are satisfiable within it.
//
// Deliberately narrow to avoid false positives: only classes/interfaces under
// "rw.itunda." are checked (framework/auto-configured beans like DataSource,
// RestTemplate, ObjectMapper are out of scope -- itunda's own wiring is the only
// thing this service's own @ComponentScan config can get wrong). Spring Data
// repository interfaces are skipped (Spring generates their implementation
// dynamically, never a manually-annotated @Component). `@Bean`-annotated factory
// methods inside `@Configuration` classes count as real providers too (found a real
// false positive this way while proving this test out: `PushSender` has zero
// `@Component` implementations, it's provided entirely via `PushConfig.pushSender()`
// -- fixed by scanning `@Bean` method return types, not just `@Component` classes).
// An interface/abstract dependency only fails if it has genuinely ZERO real
// implementations OR `@Bean` providers anywhere on this service's own classpath --
// not a heuristic guess, the exact real shape of the RealtimeMessagePublisher bug
// this test is modeled on.
class WiringTest {

    // Read directly off the real BillsServiceApplication annotation, not a
    // hand-copied duplicate -- a duplicate could silently drift from the real
    // config (test stays green while the real app is broken, or fails on a change
    // that's actually fine) and would defeat the entire point of this test.
    private val componentScan = BillsServiceApplication::class.java.getAnnotation(org.springframework.context.annotation.ComponentScan::class.java)
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

        // Full-classpath scan (not just this service's scanned packages) -- used only
        // to distinguish "genuinely no implementation/provider exists on this
        // classpath at all" (impossible to fix by adjusting scanBasePackages -- a
        // real missing dependency, e.g. RealtimeMessagePublisher) from "exists but
        // isn't scanned/is excluded" (a real, fixable scanBasePackages/excludeFilters
        // gap).
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

        assertTrue(failures.isEmpty(), "Found ${failures.size} unsatisfiable constructor dependency(s) in bills-service's own real wiring:\n" + failures.joinToString("\n"))
    }
}
