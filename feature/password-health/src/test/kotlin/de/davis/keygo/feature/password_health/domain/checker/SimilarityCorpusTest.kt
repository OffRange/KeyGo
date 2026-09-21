package de.davis.keygo.feature.password_health.domain.checker

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The corpus is the instrument every other measurement is read off, so what it calls a
 * family has to be true of the passwords rather than true of how they were generated: a
 * generator draws independently and still collides.
 */
class SimilarityCorpusTest {

    @Test
    fun readsTwoIndependentEntriesOneEditApartAsOneFamily() {
        val corpus = corpus("4rfv5tgb+" to -1, "4rfv5tgb&" to -2)

        assertEquals(setOf(pair(0, 1)), corpus.variantPairs)
    }

    /** Two edits apart is a coincidence a person could plausibly have arrived at twice. */
    @Test
    fun leavesTwoIndependentEntriesTwoEditsApartUnrelated() {
        val corpus = corpus("Phoenix*10" to -1, "Phoenix*92" to -2)

        assertEquals(emptySet(), corpus.variantPairs)
    }

    /** Spelling is not distance: one of these is the other, written differently. */
    @Test
    fun readsALeetRewriteAsTheSameFamily() {
        val corpus = corpus("Summer2019!" to -1, "5umm3r2019!" to -2)

        assertEquals(setOf(pair(0, 1)), corpus.variantPairs)
    }

    /** Whoever is one edit from both of them relates the two. */
    @Test
    fun joinsFamiliesThroughAnEntryThatSitsBetweenThem() {
        val corpus = corpus(
            "harbor-meadow-84" to 7,
            "harbor-meadow-85" to -2,
            "harbor-meadow-86" to -3,
        )

        assertEquals(setOf(pair(0, 1), pair(0, 2), pair(1, 2)), corpus.variantPairs)
    }

    /** A declared family still holds together however far apart its members drifted. */
    @Test
    fun keepsADeclaredFamilyThatNoDistanceWouldHaveFound() {
        val corpus = corpus("Baseball2008?" to 3, "Baseball2012-" to 3)

        assertEquals(setOf(pair(0, 1)), corpus.variantPairs)
    }

    @Test
    fun countsIdenticalTextAsADuplicateRatherThanAVariation() {
        val corpus = corpus("Summer2019!" to 3, "Summer2019!" to 3)

        assertEquals(emptySet(), corpus.variantPairs)
        assertEquals(setOf(pair(0, 1)), corpus.duplicatePairs)
    }

    private fun corpus(vararg entries: Pair<String, Int>) = Corpus(
        entries.mapIndexed { index, (password, family) ->
            CorpusEntry(
                id = id(index),
                password = password,
                family = family,
                archetype = "test",
                relation = if (family >= 0) "base" else "independent",
                cluster = "none",
                distanceFromBase = 0,
            )
        },
    )

    private fun id(index: Int) = UUID(0L, index.toLong())

    private fun pair(first: Int, second: Int) = PairKey.of(id(first), id(second))
}
