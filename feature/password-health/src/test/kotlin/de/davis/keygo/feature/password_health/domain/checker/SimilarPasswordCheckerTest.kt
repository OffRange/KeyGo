package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SimilarPasswordCheckerTest {

    // region what the checker is for

    @Test
    fun flagsALeetspeakRewrite() = runTest {
        assertGrouped(setOf("password!", "P@ssw0rd!"), flag("password!", "P@ssw0rd!"))
    }

    @Test
    fun flagsABumpedYear() = runTest {
        assertGrouped(setOf("Summer2023!", "Summer2024!"), flag("Summer2023!", "Summer2024!"))
    }

    @Test
    fun flagsASiteSwappedAroundASharedToken() = runTest {
        assertGrouped(setOf("facebook_Kd8!", "twitter_Kd8!"), flag("facebook_Kd8!", "twitter_Kd8!"))
    }

    @Test
    fun flagsAWrappedTokenWithTheSiteSwapped() = runTest {
        assertGrouped(
            setOf("Kq7!facebook!Kq7", "Kq7!twitter!Kq7"),
            flag("Kq7!facebook!Kq7", "Kq7!twitter!Kq7"),
        )
    }

    @Test
    fun flagsAnIncrementedCounter() = runTest {
        assertGrouped(
            setOf("Thistle-Garden-41", "Thistle-Garden-42"),
            flag("Thistle-Garden-41", "Thistle-Garden-42")
        )
    }

    // endregion

    // region a number that moved, and how far

    /**
     * A counter someone bumped moves by one; eighty-two apart is two people picking a
     * number. Without this every pair sharing a word and a symbol is reported, which was
     * the single largest group of false pairs.
     */
    @Test
    fun ignoresTwoNumbersNowhereNearEachOther() = runTest {
        assertEquals(emptySet(), flag("Phoenix*10", "Phoenix*92"))
    }

    @Test
    fun ignoresAYearThatJumpedHalfADecade() = runTest {
        assertEquals(emptySet(), flag("Ginger2021-", "Ginger2015-"))
    }

    /** A year rolling forward a couple of times is still one person editing one password. */
    @Test
    fun flagsAYearThatMovedOnAStep() = runTest {
        assertGrouped(setOf("Ginger2021-", "Ginger2023-"), flag("Ginger2021-", "Ginger2023-"))
    }

    /**
     * Two shared words say the base is the same. They do not say the rest is one edit:
     * here the punctuation changed and the counter jumped nineteen, which is two decisions
     * and therefore two passwords.
     */
    @Test
    fun ignoresATwoWordBaseThatWasRepunctuatedAndRenumbered() = runTest {
        assertEquals(emptySet(), flag("jasper-rocket-53", "jasper.rocket.34"))
    }

    /** The year and the symbol both moved, which is two decisions rather than one edit. */
    @Test
    fun ignoresATwoWordBaseWhoseYearAndSymbolBothMoved() = runTest {
        assertEquals(emptySet(), flag("P3n9uinLaurel2024@", "P3n9uinLaurel2003+"))
    }

    /**
     * Counting on the keyboard is not a secret anyone has to crack, so two weak passwords
     * that agree on nothing else are two weak passwords, not a pair.
     */
    @Test
    fun ignoresTwoWeakPasswordsSharingOnlyACountingRun() = runTest {
        assertEquals(emptySet(), flag("12345678", "1234qwer\$"))
    }

    /** A number that is not going anywhere in particular is still material. */
    @Test
    fun flagsASharedRunOfUnremarkableDigits() = runTest {
        assertGrouped(
            setOf("48213_facebook", "48213_twitter"),
            flag("48213_facebook", "48213_twitter")
        )
    }

    // endregion

    // region one password is the whole of another

    /**
     * The commonest way a base gets reused, and the edit budget is the wrong tool for it:
     * the budget is proportional to length and a site token is not.
     */
    @Test
    fun flagsATokenAppendedToAPassphrase() = runTest {
        assertGrouped(
            setOf("purple-tiger-canyon-rowan", "purple-tiger-canyon-rowanBcix9"),
            flag("purple-tiger-canyon-rowan", "purple-tiger-canyon-rowanBcix9"),
        )
    }

    /**
     * Containment is evidence even when the shared part is guessable throughout. Nothing
     * about "Summer2023" has to be cracked, but an attacker who has the longer password
     * types the shorter one in next.
     */
    @Test
    fun flagsASiteTokenAppendedToAGuessableBase() = runTest {
        assertGrouped(
            setOf("Summer2023", "Summer2023!ForWork"),
            flag("Summer2023", "Summer2023!ForWork"),
        )
    }

    @Test
    fun flagsABasePrependedTo() = runTest {
        assertGrouped(
            setOf("Kq7!Vn2Lp", "work-Kq7!Vn2Lp"),
            flag("Kq7!Vn2Lp", "work-Kq7!Vn2Lp"),
        )
    }

    /**
     * A word that happens to begin a longer word is not a password inside a password, or
     * every passphrase built on a stem drags its neighbours along.
     */
    @Test
    fun ignoresAWordThatMerelyBeginsALongerWord() = runTest {
        assertEquals(emptySet(), flag("carpenter", "carpenters"))
    }

    // endregion

    // region what belongs to another check, or to nothing

    @Test
    fun leavesExactCopiesToTheReuseCheck() = runTest {
        assertEquals(emptySet(), flag("Hunter2024!", "Hunter2024!"))
    }

    /**
     * Each copy really is a variation of the rewrite, whatever the copies are to each
     * other. What the class rules out is a relation that is nothing but a reused pair,
     * which [leavesExactCopiesToTheReuseCheck] pins.
     */
    @Test
    fun keepsExactCopiesInsideAWiderFamily() = runTest {
        val candidates = candidates("Password1", "Password1", "Password!")

        val relations = SimilarPasswordChecker().check(candidates)
            .findings
            .filterIsInstance<HealthFinding.Relation>()

        assertEquals(1, relations.size)
        assertEquals(candidates.mapTo(mutableSetOf()) { it.id }, relations.single().relatedItemIds)
    }

    @Test
    fun ignoresPasswordsTooShortToCompare() = runTest {
        assertEquals(emptySet(), flag("ab1", "ab2"))
    }

    @Test
    fun ignoresAnUnrelatedPair() = runTest {
        assertEquals(emptySet(), flag("Thistle2019#", "b7Kq-vn2LpXw"))
    }

    // endregion

    // region a shared part an attacker gets for free is not evidence

    @Test
    fun ignoresTwoAccountsOnTheSameSite() = runTest {
        assertEquals(emptySet(), flag("sparkasse-Cf9ZhV", "sparkasse-N4KEz"))
    }

    @Test
    fun ignoresAWordSharedWithAMuchShorterPassword() = runTest {
        assertEquals(emptySet(), flag("Harbor&94", "Harbor2013!"))
    }

    @Test
    fun ignoresAWordSharedWithAPassphrase() = runTest {
        assertEquals(emptySet(), flag("Golden!71", "golden.penguin.bobcat.heather"))
    }

    @Test
    fun ignoresAHabitualSuffixOverUnrelatedWords() = runTest {
        assertEquals(emptySet(), flag("Baseballviolet%41", "Parsleyviolet%41"))
    }

    // endregion

    // region a word is free however it is spelled
    //
    // The plain spelling of each of these is already quiet. Respelling the word must not
    // turn it back into evidence: leetspeak, inner capitals and shouting are what a
    // rule-based cracker undoes first, so they cost an attacker nothing.

    @Test
    fun ignoresAWordSharedInLeetSpelling() = runTest {
        assertEquals(emptySet(), flag("Dr@gon2019!!", "Dr@gon94##"))
    }

    @Test
    fun ignoresAWordSharedInMixedCase() = runTest {
        assertEquals(emptySet(), flag("PenGuIn2019_", "PenGuInCanyon1993-"))
    }

    @Test
    fun ignoresAWordSharedInCapitals() = runTest {
        assertEquals(emptySet(), flag("HARBOR&94", "HARBOR2013!"))
    }

    // endregion

    // region the difference has to be worth something too

    @Test
    fun ignoresTwoAccountsOnTheSameSiteBehindShortTokens() = runTest {
        assertEquals(emptySet(), flag("facebook_Kd8!", "facebook_Xq2!"))
    }

    @Test
    fun ignoresTwoDifferentWordsBehindTheSameCounter() = runTest {
        assertEquals(emptySet(), flag("garden22", "harden22"))
    }

    @Test
    fun ignoresAWordWhoseYearAndSymbolBothMoved() = runTest {
        assertEquals(emptySet(), flag("Vanilla2000+", "Vanilla2007#"))
    }

    // endregion

    private fun assertGrouped(expected: Set<String>, actual: Set<Set<String>>) =
        assertEquals(setOf(expected), actual)
}
