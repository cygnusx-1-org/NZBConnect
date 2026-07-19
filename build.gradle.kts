plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.spotless)
}

// Applied only at the root: the globs below are rooted at the repo, so they already cover
// the :app sources. Rules live in .editorconfig, which ktlint reads directly.
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        // .editorconfig is the source of truth for IDEs and the ktlint CLI. These two rules are
        // repeated here so the build is deterministic even when editorconfig discovery is flaky
        // over Mirakle's remote execution: @Composable functions are PascalCase by convention, and
        // the existing code has long descriptive lines ktlint cannot auto-wrap.
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(
                mapOf(
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                    "ktlint_standard_max-line-length" to "disabled",
                ),
            )
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        // .editorconfig is the source of truth for IDEs and the ktlint CLI. These two rules are
        // repeated here so the build is deterministic even when editorconfig discovery is flaky
        // over Mirakle's remote execution: @Composable functions are PascalCase by convention, and
        // the existing code has long descriptive lines ktlint cannot auto-wrap.
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(
                mapOf(
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                    "ktlint_standard_max-line-length" to "disabled",
                ),
            )
        trimTrailingWhitespace()
        endWithNewline()
    }
}
