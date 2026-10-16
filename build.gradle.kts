plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

/** Resolves every resolvable configuration; used by CI to build the offline dependency mirror. */
tasks.register("resolveAll") {
    doLast {
        allprojects.forEach { p ->
            p.configurations.filter { it.isCanBeResolved }.forEach { c ->
                runCatching { c.resolvedConfiguration.lenientConfiguration.files }
                    .onFailure { logger.warn("skip ${p.path}:${c.name}: ${it.message}") }
            }
        }
    }
}
