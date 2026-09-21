package de.davis.keygo.feature.password_health.domain.checker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The model replaced a list of English words, so the tests that matter are the ones a
 * list could not have passed: a German, French or Spanish word it was never taught.
 */
class WordModelTest {

    private fun wordAt(text: String, from: Int = 0): Int {
        val raw = text.toCharArray()
        return WordModel.wordRunAt(raw.canonicalize(), raw, from, raw.size)
    }

    // region words a list would have held

    @Test
    fun readsAPlainEnglishWord() = assertEquals(6, wordAt("dragon"))

    @Test
    fun readsAShoutedWord() = assertEquals(6, wordAt("HARBOR"))

    @Test
    fun readsALeetedWord() = assertEquals(6, wordAt("Dr@gon"))

    // endregion

    // region words no English list holds

    @Test
    fun readsAGermanWordItWasNeverTaught() = assertEquals(9, wordAt("kartoffel"))

    @Test
    fun readsALeetedGermanWord() = assertEquals(9, wordAt("K@rtoffel"))

    @Test
    fun readsAFrenchWordItWasNeverTaught() = assertEquals(8, wordAt("papillon"))

    @Test
    fun readsAnItalianWordItWasNeverTaught() = assertEquals(8, wordAt("farfalla"))

    /**
     * How much of a word survives, language by language, which is the claim the whole
     * change rests on. Reading a word most of the way through is enough for what the
     * checker asks; reading it whole matters only for counting words.
     *
     * Measured: English 100%, Spanish 100%, French 96%, Italian 95%, German 76%, Polish
     * 61%. Polish is the floor because its spelling is the furthest from the training set.
     * The English list this replaced read 0% of every word below that is not English.
     */
    @Test
    fun readsMostOfAWordInEveryLanguageAroundEnglish() {
        val byLanguage = mapOf(
            "english" to listOf(
                "dragon", "harbor", "summer", "monkey", "sunshine", "butterfly",
                "freedom", "mountain", "chocolate", "elephant", "whisper", "garden",
            ),
            "german" to listOf(
                "kartoffel", "sonnenschein", "freiheit", "schmetterling", "blumenwiese",
                "hoffnung", "wasserfall", "geburtstag", "fernweh", "morgenrot",
            ),
            "french" to listOf(
                "papillon", "bonjour", "liberte", "chocolat", "printemps", "bibliotheque",
                "tournesol", "pamplemousse", "brouillard", "chaussure",
            ),
            "spanish" to listOf(
                "mariposa", "primavera", "libertad", "corazon", "ventana", "estrella",
                "murcielago", "zanahoria", "esperanza", "caballero",
            ),
            "italian" to listOf(
                "farfalla", "amicizia", "montagna", "girasole", "biblioteca",
                "cioccolato", "finestra", "speranza", "tramonto",
            ),
            "polish" to listOf(
                "przyjaciel", "wolnosc", "motyl", "sloneczny", "biblioteka", "marzenie",
            ),
        )

        byLanguage.forEach { (language, words) ->
            val read = words.sumOf { wordAt(it) }.toDouble() / words.sumOf { it.length }
            assertTrue(read >= 0.60, "only ${"%.0f".format(read * 100)}% of $language read")
        }
    }

    // endregion

    // region what an attacker still has to crack

    @Test
    fun rejectsAShortRandomToken() = assertEquals(0, wordAt("Kd8!"))

    @Test
    fun rejectsALongerRandomToken() = assertEquals(0, wordAt("b7Kqxz"))

    @Test
    fun rejectsAKeyboardWalk() = assertEquals(0, wordAt("4rfv5tgb"))

    @Test
    fun rejectsARunTooShortToBeAWord() = assertEquals(0, wordAt("ab"))

    /** "1337" canonicalises to "ieet". One real letter is what tells them apart. */
    @Test
    fun rejectsDigitsThatFoldIntoLetters() = assertEquals(0, wordAt("1337"))

    /**
     * What a model costs that a list did not: "trewq" is spelled the way words are and is
     * not one. The price is paid back by every word of every language a list never held,
     * and `SimilarPasswordCheckerBenchmark` weighs the two against each other.
     */
    @Test
    fun admitsASpellingThatIsNotAWord() = assertEquals(5, wordAt("trewq"))

    // endregion

    // region where a word ends

    @Test
    fun stopsWhereTheLettersStop() = assertEquals(6, wordAt("dragon2019"))

    @Test
    fun readsTheWordStartingAtTheOffsetItIsGiven() = assertEquals(6, wordAt("2019dragon", from = 4))

    /**
     * Canonicalising turns "1993+" into five more letters, so the run is eleven long and
     * only the model says where the word inside it stopped.
     */
    @Test
    fun stopsWhereTheWordEndsInsideALongerRunOfLetters() =
        assertEquals(6, wordAt("Harbor1993+"))

    /**
     * The other thing a model costs. Two words written without anything between them read
     * as one long word, because only a list knew where the first one ended.
     *
     * The right answer about cost, since all twelve characters are free either way, and
     * the wrong answer about count: `PasswordShape.wordsWithin` sees one word where a
     * person wrote two. That is why [PasswordShape] tries shape before the model at all,
     * since "MeadowMeadow" as a person would usually write it splits on the capital.
     */
    @Test
    fun readsTwoWordsRunTogetherAsOne() = assertEquals(12, wordAt("meadowmeadow"))

    /**
     * A run written in an alphabet the model cannot score is still letters in a row, and
     * letters in a row in a password are a word far more often than they are a secret.
     */
    @Test
    fun keepsARunItHasNoWayToScore() = assertEquals(6, wordAt("пароль"))

    // endregion
}
