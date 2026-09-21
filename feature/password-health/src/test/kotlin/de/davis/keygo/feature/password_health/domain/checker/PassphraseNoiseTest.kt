package de.davis.keygo.feature.password_health.domain.checker

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class PassphraseNoiseTest {

    private val words = SimilarityCorpus.passphraseWords

    @Test
    fun oneStyleThroughoutAVaultRaisesNothing() = runTest(timeout = 5.minutes) {
        Style.entries.forEach { style ->
            val vault = List(250) { style.build(words, Random(it.toLong() + style.ordinal * 1000)) }
                .distinct()

            val flagged = flag(vault)

            assertEquals(
                emptySet(),
                flagged,
                "style ${style.name} raised ${flagged.size} groups among independent passphrases",
            )
        }
    }

    @Test
    fun aNarrowPersonalVocabularyStaysNearlyQuiet() = runTest(timeout = 5.minutes) {
        val narrow = words.take(40)
        val vault = List(250) { Style.HyphenLower.build(narrow, Random(it.toLong())) }.distinct()

        val flagged = flag(vault)

        assertTrue(
            flagged.size <= vault.size / 25,
            "${flagged.size} groups among ${vault.size} passphrases from a 40 word vocabulary: $flagged",
        )
    }

    @Test
    fun sharingOneWordIsNotEnough() = runTest {
        assertEquals(
            emptySet(),
            flag("correct-horse-battery-staple", "correct-otter-lantern-puzzle"),
        )
    }

    @Test
    fun sharingTwoLeadingWordsIsNotEnough() = runTest {
        assertEquals(
            emptySet(),
            flag("correct-horse-battery-staple", "correct-horse-lantern-puzzle"),
        )
    }

    @Test
    fun sharingTwoLeadingWordsIsNotEnoughInCapitals() = runTest {
        assertEquals(
            emptySet(),
            flag("MAPLE-CEDAR-WILLOW-ASPEN", "MAPLE-CEDAR-SPRUCE-JUNIPER"),
        )
    }

    @Test
    fun sharingTwoLeadingShortWordsIsNotEnough() = runTest {
        assertEquals(emptySet(), flag("Oak-Elk-Ivy-Fig-Owl", "Oak-Elk-Ant-Bee-Cow"))
    }

    @Test
    fun sharingEveryWordButOneIsStillReported() = runTest {
        val flagged = flag("correct-horse-battery-staple", "correct-horse-battery-puzzle")

        assertEquals(1, flagged.size)
    }

    @Test
    fun theSamePassphraseWithADigitAppendedIsStillReported() = runTest {
        val flagged = flag("correct-horse-battery-staple", "correct-horse-battery-staple7")

        assertEquals(1, flagged.size)
    }

    @Test
    fun theSamePassphraseRepunctuatedIsStillReported() = runTest {
        val flagged = flag("correct-horse-battery-staple", "correct.horse.battery.staple")

        assertEquals(1, flagged.size)
    }

    private enum class Style(
        private val count: IntRange,
        private val separator: Char,
        private val capitalise: Boolean,
        private val trailingDigits: Int,
    ) {
        HyphenLower(3..3, '-', false, 0),
        HyphenLowerLong(6..6, '-', false, 0),
        DotCapitalised(4..4, '.', true, 0),
        UnderscoreWithDigits(4..4, '_', false, 2),
        SpacedLower(5..5, ' ', false, 0),
        ;

        fun build(words: List<String>, random: Random): String {
            val chosen = List(random.nextInt(count.first, count.last + 1)) {
                val word = words.random(random)
                if (capitalise) word.replaceFirstChar { char -> char.uppercase() } else word
            }
            val digits = (1..trailingDigits).joinToString("") { random.nextInt(10).toString() }
            return chosen.joinToString(separator.toString()) + digits
        }
    }
}
