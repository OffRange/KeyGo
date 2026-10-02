package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A copy of [SimilarPasswordChecker]'s decision with its constants opened up, so the
 * operating point can be moved and measured without touching the shipped checker.
 *
 * Only the decision is duplicated. Everything it leans on (canonicalisation, the
 * character classification in [PasswordShape], the word model, the bounded distance) is
 * the real code from PasswordSimilarity.kt, and
 * `SimilaritySensitivitySweep.replicaMatchesShippedChecker` pins the default settings to
 * the shipped output, so a drift in either one fails.
 */
internal class TunableSimilarity(
    private val minLength: Int = 6,
    private val minTemplateLength: Int = 8,
    private val minAffixLength: Int = 4,
    /** Zero switches the containment rule off entirely. */
    private val minContainedLength: Int = 8,
    /** How far a number may travel and still read as a bump. Int.MAX_VALUE switches it off. */
    private val maxCounterStep: Long = 3,
    private val minSharedWords: Int = 1,
    private val sharedWordsAsEvidence: Int = 2,
    /** What shared words alone are worth. 1 is the old behaviour: the full budget. */
    private val weakEvidenceDivisor: Int = 1,
    private val affixShare: Double = 0.4,
    private val wordAffixShare: Double = 0.55,
    private val maxDistanceRatio: Int = 4,
    /** A ceiling on the edit budget however long the passwords are. */
    private val maxDistance: Int = 4,
    private val denominator: Denominator = Denominator.LongerSecret,
    /** Whether an end that reads as words on both sides stops counting entirely. */
    private val ignoreWordAffixes: Boolean = true,
    /**
     * Whether a pair the edit budget accepts still has to share something, or differ only
     * in free filler, before it is reported. Switching this off is what the edit rule used
     * to do, and it produced most of the false pairs.
     */
    private val requireEvidence: Boolean = true,
    /**
     * Whether the two ends are stopped from claiming the same characters twice. Without it
     * a short password whose prefix and suffix overlap can score above 1.0 on its own
     * length alone.
     */
    private val capOverlap: Boolean = false,
) {

    enum class Denominator { Shorter, Longer, Mean, LongerSecret }

    /** Which rule fired for a flagged pair. */
    enum class Rule { LeetFamily, Contains, Template, Distance }

    fun edges(candidates: List<PasswordCandidate>): Map<PairKey, Rule> {
        val shapes = candidates.map { it to PasswordShape(it.password) }
        val comparable = shapes.filter { (_, shape) -> shape.size >= minLength }
        val edges = LinkedHashMap<PairKey, Rule>()

        comparable.groupBy { (_, shape) -> CharArrayKey(shape.canonical) }
            .values
            .filter { family -> family.distinctBy { CharArrayKey(it.first.password) }.size > 1 }
            .forEach { family ->
                for (i in family.indices) for (j in i + 1..family.lastIndex)
                    edges[PairKey.of(family[i].first.id, family[j].first.id)] = Rule.LeetFamily
            }

        val rows = DistanceRows(comparable.maxOfOrNull { (_, shape) -> shape.size } ?: 0)
        for (i in comparable.indices) {
            val (first, firstShape) = comparable[i]
            for (j in i + 1..comparable.lastIndex) {
                val (second, secondShape) = comparable[j]
                if (first.password.contentEquals(second.password)) continue
                if (firstShape.canonical.contentEquals(secondShape.canonical)) continue

                val rule = rule(firstShape, secondShape, rows)
                if (rule != null) edges.putIfAbsent(PairKey.of(first.id, second.id), rule)
            }
        }
        return edges
    }

    private fun rule(first: PasswordShape, second: PasswordShape, rows: DistanceRows): Rule? {
        val shorter = min(first.size, second.size)
        val prefix = commonPrefixLength(first.canonical, second.canonical)
        val suffix = commonSuffixLength(first.canonical, second.canonical)

        if (minContainedLength > 0 && extendsTheOther(first, second, prefix, suffix))
            return Rule.Contains
        if (sharesTemplate(first, second, prefix, suffix, shorter)) return Rule.Template

        val allowed = min(max(1, shorter / maxDistanceRatio), maxDistance)
        val budget = if (requireEvidence) editBudget(first, second, prefix, suffix, allowed)
        else allowed
        if (budget <= 0) return null

        return if (boundedDistance(first.canonical, second.canonical, budget, rows) <= budget)
            Rule.Distance
        else null
    }

    private fun extendsTheOther(
        first: PasswordShape,
        second: PasswordShape,
        prefix: Int,
        suffix: Int,
    ): Boolean {
        val shorter = min(first.size, second.size)
        if (shorter < minContainedLength) return false
        if (first.size == second.size) return false

        val longer = if (first.size > second.size) first else second
        if (prefix >= shorter) return longer.startsRun(shorter)
        if (suffix >= shorter) return longer.startsRun(longer.size - shorter)
        return false
    }

    private fun editBudget(
        first: PasswordShape,
        second: PasswordShape,
        prefix: Int,
        suffix: Int,
        allowed: Int,
    ): Int {
        val firstEnd = max(prefix, first.size - suffix)
        val secondEnd = max(prefix, second.size - suffix)
        val before = first.numberAround(prefix, firstEnd)
        val after = second.numberAround(prefix, secondEnd)
        if (before >= 0 && after >= 0 && abs(before - after) > maxCounterStep) return 0

        val shared = prefixAffix(first, second, prefix).length +
                suffixAffix(first, second, suffix).length
        if (shared >= minAffixLength) return allowed

        val words = min(sharedWords(first, prefix, suffix), sharedWords(second, prefix, suffix))
        if (words < minSharedWords) return 0

        val oneStep = first.holdsNothingToCrack(prefix, firstEnd) &&
                second.holdsNothingToCrack(prefix, secondEnd) ||
                first.movesOneSlot(prefix, firstEnd) && second.movesOneSlot(prefix, secondEnd)
        if (oneStep) return allowed

        return if (words >= sharedWordsAsEvidence) max(1, allowed / weakEvidenceDivisor) else 0
    }

    private fun sharedWords(shape: PasswordShape, prefix: Int, suffix: Int): Int {
        val tail = max(prefix, shape.size - suffix)
        return shape.wordsWithin(0, prefix) + shape.wordsWithin(tail, shape.size)
    }

    /** One shared end: what it is worth, and whether it reads as words on both sides. */
    private class Affix(val length: Int, val isWord: Boolean)

    private fun prefixAffix(first: PasswordShape, second: PasswordShape, prefix: Int): Affix {
        val isWord = first.readsAsWords(0, prefix) && second.readsAsWords(0, prefix)
        val length = if (ignoreWordAffixes && isWord) 0
        else min(first.secretLength(0, prefix), second.secretLength(0, prefix))
        return Affix(length, isWord)
    }

    private fun suffixAffix(first: PasswordShape, second: PasswordShape, suffix: Int): Affix {
        val isWord = first.readsAsWords(first.size - suffix, first.size) &&
                second.readsAsWords(second.size - suffix, second.size)
        val length = if (ignoreWordAffixes && isWord) 0
        else min(
            first.secretLength(first.size - suffix, first.size),
            second.secretLength(second.size - suffix, second.size),
        )
        return Affix(length, isWord)
    }

    private fun sharesTemplate(
        first: PasswordShape,
        second: PasswordShape,
        prefix: Int,
        suffix: Int,
        shorter: Int,
    ): Boolean {
        if (shorter < minTemplateLength) return false

        val head = prefixAffix(first, second, prefix)
        val tail = suffixAffix(first, second, suffix)
        // Zeroed before the length gate, the way the checker does it: an end that counts
        // for nothing must not carry the pair past the minimum either.
        val tailLength = if (capOverlap) min(tail.length, shorter - min(head.length, shorter))
        else tail.length
        if (head.length + tailLength < minAffixLength) return false

        val longer = if (first.size >= second.size) first else second
        val scale = when (denominator) {
            Denominator.Shorter -> shorter.toDouble()
            Denominator.Longer -> max(first.size, second.size).toDouble()
            Denominator.Mean -> (first.size + second.size) / 2.0
            Denominator.LongerSecret -> longer.secretLength(0, longer.size).toDouble()
        }
        return contribution(head.length, scale, head.isWord) +
                contribution(tailLength, scale, tail.isWord) >= 1.0
    }

    private fun contribution(length: Int, scale: Double, isWord: Boolean): Double = when {
        isWord && ignoreWordAffixes -> 0.0
        scale <= 0.0 -> if (length > 0) Double.MAX_VALUE else 0.0
        else -> length / ((if (isWord) wordAffixShare else affixShare) * scale)
    }
}
