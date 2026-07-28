package de.davis.keygo.feature.auth.presentation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `AuthViewModel` cannot be built in a JVM unit test at all: its `SavedStateHandle.toRoute` call
 * lands in `android.os.BaseBundle`, which throws "not mocked" here. So what the import actually
 * does is covered where it can be, in `LegacyImportRunnerTest` over in `:migration:legacy-data`,
 * and what is left for this file is the wiring that test assumes. Asserting on source text is a
 * weak way to do it, but the thing it catches is real: someone adding a fifth way into the app and
 * forgetting the import.
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
            Regex("""startLegacyDataImport\(\)""").findAll(viewModelSource).count(),
            "Expected the import on the password-create, biometric-create, password-unlock " +
                "and biometric-unlock paths.",
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
