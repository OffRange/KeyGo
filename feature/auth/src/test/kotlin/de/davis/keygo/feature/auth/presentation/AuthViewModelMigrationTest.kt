package de.davis.keygo.feature.auth.presentation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `AuthViewModel` cannot be built in a JVM unit test at all: its `SavedStateHandle.toRoute` call
 * lands in `android.os.BaseBundle`, which throws "not mocked" here. So the behaviour that matters
 * is covered where it can be, in `LegacyDataImportTest`, and what is left for this file is the
 * wiring those tests assume. What actually regresses is someone adding a fifth way into the app and
 * forgetting the import, or tidying [importLegacyData] out of a call site because it looks like
 * ceremony.
 *
 * If you add another successful-session-start path, add the call and bump the expected count.
 */
class AuthViewModelMigrationTest {

    private val viewModelSource = sourceOf("AuthViewModel")

    private val screenSource = sourceOf("AuthScreen")

    @Test
    fun `every successful session start runs the legacy migration`() {
        assertEquals(
            4,
            Regex("""migrateLegacyData\(\)""").findAll(viewModelSource).count(),
            "Expected the migration on the password-create, biometric-create, password-unlock " +
                "and biometric-unlock paths.",
        )
    }

    @Test
    fun `no call site lets the migration into the unlock flow unguarded`() {
        assertEquals(
            4,
            Regex("""importLegacyData\s*\{\s*migrateLegacyData\(\)\s*}""")
                .findAll(viewModelSource)
                .count(),
            "Every migration call has to go through importLegacyData. Called bare, a throw or a " +
                "hang in the import skips the navigation that follows it and strands the user on " +
                "the lock screen with a live session behind it.",
        )
    }

    @Test
    fun `biometric unlock hands control back to the view model`() {
        assertTrue(
            screenSource.contains("viewModel.onBiometricUnlockSucceeded()"),
            "Biometric unlock must not call onSuccess directly; the migration would never run.",
        )
        assertTrue(
            viewModelSource.contains("fun onBiometricUnlockSucceeded()"),
            "AuthScreen calls onBiometricUnlockSucceeded; AuthViewModel must declare it.",
        )
    }

    private fun sourceOf(name: String): String {
        val file = File("src/main/kotlin/de/davis/keygo/feature/auth/presentation/$name.kt")
        // Read relative to the module directory, which is where Gradle runs these tests from. Named
        // rather than left to fail on an empty string, so a runner with a different working
        // directory says so instead of turning every assertion below into a puzzle.
        assertTrue(file.exists(), "Expected to find $name.kt at ${file.absolutePath}")

        return file.readText()
    }
}
