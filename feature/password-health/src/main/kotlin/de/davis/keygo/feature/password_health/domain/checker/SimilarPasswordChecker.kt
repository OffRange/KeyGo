package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.feature.password_health.domain.checker.SimilarPasswordChecker.Companion.AFFIX_SHARE
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.CheckOutcome
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate
import de.davis.keygo.feature.password_health.domain.model.RelationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.math.abs

/**
 * Flags passwords that are variations of one another rather than outright copies:
 * leetspeak rewrites ("P@ssw0rd!" against "password!") and templates where only one part
 * moved ("Summer2023!" against "Summer2024!", "facebook_Kd8!" against "twitter_Kd8!").
 *
 * Exact copies are left to [ReusePasswordCheck] on purpose. Reporting them here too would
 * hang a second edge on every reused pair and count the same logins again in the summary
 * line. Copies still appear inside a wider family, which is not the same thing: two
 * copies of a password sitting beside a rewrite of it are each a variation of that
 * rewrite, so all of them belong in the one relation.
 */
@Single
internal class SimilarPasswordChecker : PasswordHealthChecker {

    override val type = CheckKind.Similarity

    override suspend fun check(candidates: List<PasswordCandidate>): CheckOutcome =
        withContext(Dispatchers.Default) {
            // A shape holds a canonical form of the password, lowercased and leet-folded,
            // which still leaves it readable. It gets the same wipe as the original.
            val shapes = candidates.map { it to PasswordShape(it.password) }

            val relations = try {
                val comparable = shapes.filter { (_, shape) -> shape.size >= MIN_LENGTH }

                val edges = mutableSetOf<Set<ItemId>>()
                collectLeetFamilies(comparable, into = edges)
                collectVariantPairs(comparable, into = edges)

                edges.map {
                    HealthFinding.Relation(relatedItemIds = it, type = RelationType.Similar)
                }
            } finally {
                shapes.forEach { (_, shape) -> shape.wipe() }
            }

            CheckOutcome(findings = relations)
        }

    private fun collectLeetFamilies(
        comparable: List<Pair<PasswordCandidate, PasswordShape>>,
        into: MutableSet<Set<ItemId>>,
    ) {
        comparable.groupBy { (_, shape) -> CharArrayKey(shape.canonical) }
            .values
            .filter { family -> family.distinctBy { CharArrayKey(it.first.password) }.size > 1 }
            .mapTo(into) { family -> family.mapTo(mutableSetOf()) { (candidate, _) -> candidate.id } }
    }

    private suspend fun collectVariantPairs(
        comparable: List<Pair<PasswordCandidate, PasswordShape>>,
        into: MutableSet<Set<ItemId>>,
    ) {
        val rows = DistanceRows(comparable.maxOfOrNull { (_, shape) -> shape.size } ?: return)

        for (i in comparable.indices) {
            currentCoroutineContext().ensureActive()
            val (first, firstShape) = comparable[i]

            for (j in i + 1..comparable.lastIndex) {
                val (second, secondShape) = comparable[j]

                if (first.password.contentEquals(second.password)) continue
                if (firstShape.canonical.contentEquals(secondShape.canonical)) continue
                if (isVariant(firstShape, secondShape, rows)) into += setOf(first.id, second.id)
            }
        }
    }

    private fun isVariant(
        first: PasswordShape,
        second: PasswordShape,
        rows: DistanceRows,
    ): Boolean {
        val prefix = commonPrefixLength(first.canonical, second.canonical)
        val suffix = commonSuffixLength(first.canonical, second.canonical)
        val shared = weighPrefix(first, second, prefix) + weighSuffix(first, second, suffix)

        if (extendsTheOther(first, second, prefix, suffix)) return true
        if (sharesTemplate(first, second, shared)) return true
        if (!carriesEvidence(first, second, prefix, suffix, shared)) return false

        val allowed = (minOf(first.size, second.size) / MAX_DISTANCE_RATIO)
            .coerceIn(1, MAX_DISTANCE)
        return boundedDistance(first.canonical, second.canonical, allowed, rows) <= allowed
    }

    /**
     * Whether one password is the whole of the other with something added at one end.
     *
     * The edit budget cannot answer this: it is proportional to length and an appended
     * site token is not, so past three or four characters the budget rules out a pair one
     * of which plainly contains the other.
     *
     * Containment is evidence even when the shared part is guessable from end to end.
     * Nothing in "Summer2023" has to be cracked, but an attacker holding
     * "Summer2023!ForWork" tries it anyway, and that is the question being asked.
     *
     * What it must not do is read a stem as a password. The added part has to begin where
     * a run begins, or "carpenter" pulls in "carpenters" and every passphrase built on a
     * shared stem drags its neighbours in with it.
     */
    private fun extendsTheOther(
        first: PasswordShape,
        second: PasswordShape,
        prefix: Int,
        suffix: Int,
    ): Boolean {
        val shorter = minOf(first.size, second.size)
        if (shorter < MIN_CONTAINED_LENGTH) return false
        if (first.size == second.size) return false

        val longer = if (first.size > second.size) first else second
        if (prefix >= shorter) return longer.startsRun(shorter)
        if (suffix >= shorter) return longer.startsRun(longer.size - shorter)
        return false
    }

    /**
     * Whether the two were built from the same template with one part moved.
     *
     * Only shared material an attacker would have to crack counts. A year, a dictionary
     * word and a site name are guessed rather than cracked, so [PasswordShape.secretLength]
     * takes them out of the shared ends, and an end that reads as words the whole way
     * along counts for nothing at all. What is left has to cover [AFFIX_SHARE] of the
     * secret material in the longer password, so a short password cannot pull a long one
     * in behind a prefix that merely happens to be most of it.
     */
    private fun sharesTemplate(first: PasswordShape, second: PasswordShape, shared: Int): Boolean {
        if (minOf(first.size, second.size) < MIN_TEMPLATE_LENGTH) return false
        if (shared < MIN_AFFIX_LENGTH) return false

        val longer = if (first.size >= second.size) first else second
        return shared >= AFFIX_SHARE * longer.secretLength(0, longer.size)
    }

    /**
     * Whether a pair the edit budget would accept is worth reporting at all.
     *
     * Being a few edits apart is not the same as being the same password. Two short
     * passwords built the same way land near each other by construction, which is why
     * almost every false pair used to come from the edit rule.
     *
     * Sharing material an attacker has to crack is evidence on its own ("b7Kq-vn2LpXw"
     * with one letter of difference). Failing that, the shared ends have to hold at least
     * one whole word, and then the difference has to be the kind a person makes to their
     * own password rather than the kind that tells two passwords apart:
     *
     *  - nothing to crack changed and no whole word went with it, so the edit landed
     *    inside a word, a year or the punctuation ("harbor_meadow_84" against
     *    "harbr_meadow_84"), or
     *  - exactly one slot moved ("Summer2023!" against "Summer2024!").
     *
     * Sharing two whole words is evidence on its own. Letting it buy only a smaller edit
     * budget instead bought under a point of precision and cost more than two of recall;
     * what actually separates "P3n9uinLaurel2024@" from "P3n9uinLaurel2003+" is
     * [numbersMovedByAStep].
     *
     * What this turns away is the pair with none of that: "garden22" beside "harden22",
     * where the edit broke the only word there was; "facebook_Kd8!" beside
     * "facebook_Xq2!", where the shared part is a site name and each side keeps a token
     * the other says nothing about; and "Vanilla2000+" beside "Vanilla2007#", where two
     * slots moved at once, which is two choices rather than one edit.
     */
    private fun carriesEvidence(
        first: PasswordShape,
        second: PasswordShape,
        prefix: Int,
        suffix: Int,
        shared: Int,
    ): Boolean {
        val firstEnd = maxOf(prefix, first.size - suffix)
        val secondEnd = maxOf(prefix, second.size - suffix)
        if (!numbersMovedByAStep(first, second, prefix, firstEnd, secondEnd)) return false

        if (shared >= MIN_AFFIX_LENGTH) return true

        val words = minOf(sharedWords(first, prefix, suffix), sharedWords(second, prefix, suffix))
        if (words >= SHARED_WORDS_AS_EVIDENCE) return true
        if (words < MIN_SHARED_WORDS) return false

        return first.holdsNothingToCrack(prefix, firstEnd) &&
                second.holdsNothingToCrack(prefix, secondEnd) ||
                first.movesOneSlot(prefix, firstEnd) && second.movesOneSlot(prefix, secondEnd)
    }

    /**
     * Whether the numbers the two disagree about moved by a step a person makes.
     *
     * A counter someone bumped goes up by one, and a year rolls on by one or two. Two
     * numbers that merely occupy the same slot land wherever they land, so eighty-two
     * apart is not a bump, it is two people picking a number. Nothing else separates
     * "Phoenix*10" beside "Phoenix*92" from "Summer2023!" beside "Summer2024!".
     *
     * The numbers compared are the whole ones the difference falls inside, not the part of
     * them that differs, so a year and the symbol after it moving together is still read
     * as 2024 against 2003. Anything that is not one number on both sides is left to the
     * rules that follow.
     */
    private fun numbersMovedByAStep(
        first: PasswordShape,
        second: PasswordShape,
        prefix: Int,
        firstEnd: Int,
        secondEnd: Int,
    ): Boolean {
        val before = first.numberAround(prefix, firstEnd)
        val after = second.numberAround(prefix, secondEnd)
        if (before < 0 || after < 0) return true

        return abs(before - after) <= MAX_COUNTER_STEP
    }

    /** Whole words inside the shared ends. The two ends can overlap on a short password. */
    private fun sharedWords(shape: PasswordShape, prefix: Int, suffix: Int): Int {
        val tail = maxOf(prefix, shape.size - suffix)
        return shape.wordsWithin(0, prefix) + shape.wordsWithin(tail, shape.size)
    }

    private fun weighPrefix(first: PasswordShape, second: PasswordShape, prefix: Int): Int {
        if (first.readsAsWords(0, prefix) && second.readsAsWords(0, prefix)) return 0

        return minOf(first.secretLength(0, prefix), second.secretLength(0, prefix))
    }

    private fun weighSuffix(first: PasswordShape, second: PasswordShape, suffix: Int): Int {
        if (first.readsAsWords(first.size - suffix, first.size) &&
            second.readsAsWords(second.size - suffix, second.size)
        ) return 0

        return minOf(
            first.secretLength(first.size - suffix, first.size),
            second.secretLength(second.size - suffix, second.size),
        )
    }

    companion object {
        /**
         * Below this, similarity stops meaning anything: short passwords collide by
         * accident, and the strength check already reports them.
         */
        private const val MIN_LENGTH = 6

        /** The shared-affix rule needs real length behind it to stay quiet. */
        private const val MIN_TEMPLATE_LENGTH = 8
        private const val MIN_AFFIX_LENGTH = 4

        /**
         * How much of a password has to sit inside another before containment means
         * anything. Short strings turn up inside longer ones by accident.
         */
        private const val MIN_CONTAINED_LENGTH = 8

        /**
         * Whole words the shared ends have to keep before an edit-distance hit is looked at
         * at all. One is what separates a typo from a word swap: an edit inside the only
         * word there was leaves no whole word behind it.
         */
        private const val MIN_SHARED_WORDS = 1

        /**
         * The share of the longer password's secret material the shared part has to cover.
         * Tuned on a labelled corpus; see SimilaritySensitivitySweep for what moving it
         * costs in recall.
         */
        private const val AFFIX_SHARE = 0.4

        /**
         * Shared words that are evidence on their own, whatever the difference is made of.
         * Two is the point where a wordlist stops explaining the overlap: unrelated
         * passphrases share one word constantly and three almost never.
         */
        private const val SHARED_WORDS_AS_EVIDENCE = 2

        /**
         * How far a number may travel and still read as one someone bumped rather than two
         * numbers that happen to sit in the same slot. People increment; they do not jump.
         * Wide enough that a year skipping a couple of renewals survives.
         */
        private const val MAX_COUNTER_STEP = 3

        /** One edit per this many characters still counts as the same password. */
        private const val MAX_DISTANCE_RATIO = 4

        /**
         * However long the two are, never allow more edits than this. A real variation
         * moves one slot: a typo, a counter, a symbol, a year. The proportional budget on
         * its own gives a twenty-eight character passphrase seven edits, which is a whole
         * word, and two unrelated passphrases off the same wordlist then start looking like
         * one another.
         */
        private const val MAX_DISTANCE = 4
    }
}
