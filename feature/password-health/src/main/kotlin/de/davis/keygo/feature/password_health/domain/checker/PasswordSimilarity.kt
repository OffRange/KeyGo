package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.feature.password_health.domain.checker.PasswordShape.Companion.FREE
import de.davis.keygo.feature.password_health.domain.checker.PasswordShape.Companion.SECRET
import de.davis.keygo.feature.password_health.domain.checker.PasswordShape.Companion.SEPARATOR
import de.davis.keygo.feature.password_health.domain.checker.PasswordShape.Companion.WORD
import kotlin.math.abs

private val LEET_CLASSES = mapOf(
    'i' to setOf('l', '1', '|', '!'),
    'o' to setOf('0'),
    'a' to setOf('@', '4'),
    'e' to setOf('3'),
    's' to setOf('5', '$'),
    't' to setOf('7', '+'),
    'b' to setOf('8'),
    'g' to setOf('9'),
)

private val LEET_FOLD = LEET_CLASSES.flatMap { (key, values) -> values.map { it to key } }.toMap()

internal fun CharArray.canonicalize(): CharArray = CharArray(size) { index ->
    val lower = this[index].lowercaseChar()
    LEET_FOLD[lower] ?: lower
}

private const val WORD_SEPARATORS = "-_. "

/** Letters in a row that read as a word even when no list has it: a site, a surname. */
private const val WORD_RUN = 4

/** Digits in a row short enough to be a counter rather than a secret. */
private const val SHORT_DIGIT_RUN = 3

private const val YEAR_LENGTH = 4

/**
 * What each character of one password costs an attacker, worked out once.
 *
 * Every pair in the vault asks the same questions about the same passwords, and each
 * answer depends on one password alone. Classifying up front turns every later question
 * into an array subtraction, which is what makes a dictionary lookup affordable inside a
 * sweep that is quadratic in the size of the vault.
 *
 * Characters fall into four kinds:
 *
 *  - [WORD]: a run of letters [WordModel] reads as a word, in any spelling, or a run long
 *    enough that its shape alone says so. Free.
 *  - [FREE]: a four digit year, or a run of three digits or fewer. A year falls in a few
 *    dozen tries and a short counter is habit, so neither is evidence.
 *  - [SEPARATOR]: what holds a passphrase together. Free inside a run of words, but not
 *    on its own.
 *  - [SECRET]: everything else, which is what an attacker actually has to crack.
 *
 * The whole password is classified, never a fragment of it, so a word cut in half by a
 * shared prefix is still recognised as the word it is part of.
 */
internal class PasswordShape(private val raw: CharArray) {

    val size = raw.size

    val canonical: CharArray = raw.canonicalize()

    private val kind = ByteArray(size)

    /** Where the run holding each character began, so "one slot moved" is answerable. */
    private val slotStart = IntArray(size)

    /** Word runs in order, as parallel start/end arrays. Disjoint by construction. */
    private val wordStarts: IntArray
    private val wordEnds: IntArray

    /** Running counts, so every range question below is two array reads. */
    private val secretBefore = IntArray(size + 1)
    private val crackableBefore = IntArray(size + 1)
    private val unwordlikeBefore = IntArray(size + 1)

    init {
        val starts = ArrayList<Int>()
        val ends = ArrayList<Int>()
        classify(starts, ends)
        wordStarts = starts.toIntArray()
        wordEnds = ends.toIntArray()

        for (index in 0 until size) {
            secretBefore[index + 1] = secretBefore[index] + if (isSecret(index)) 1 else 0
            crackableBefore[index + 1] =
                crackableBefore[index] + if (kind[index] == SECRET) 1 else 0
            unwordlikeBefore[index + 1] =
                unwordlikeBefore[index] + if (isWordlike(index)) 0 else 1
        }
    }

    /**
     * Length of `[from, until)` once the parts an attacker gets for free are discounted.
     *
     * A separator counts, because on its own it is a character like any other. A run of
     * them between words does not, which is what [readsAsWords] is for.
     */
    fun secretLength(from: Int, until: Int): Int =
        if (from >= until) 0 else countBetween(secretBefore, from, until)

    /**
     * Whether `[from, until)` is nothing but words and the separators between them. Such
     * an end counts for nothing at all, however long it is: two passwords that share only
     * "correct-horse-" share only what a wordlist hands out.
     */
    fun readsAsWords(from: Int, until: Int): Boolean =
        from < until && countBetween(unwordlikeBefore, from, until) == 0

    /**
     * Whether `[from, until)` holds nothing an attacker would have to crack.
     *
     * Unlike [secretLength] a separator does not count here. The question this answers is
     * what the difference between two passwords is made of, and a passphrase written with
     * dots instead of hyphens has not changed its secret.
     */
    fun holdsNothingToCrack(from: Int, until: Int): Boolean =
        from >= minOf(until, size) || countBetween(crackableBefore, from, until) == 0

    /** Whole words inside `[from, until)`. A word cut by the range does not count. */
    fun wordsWithin(from: Int, until: Int): Int {
        var count = 0
        for (index in wordStarts.indices)
            if (wordStarts[index] >= from && wordEnds[index] <= until) count++
        return count
    }

    /**
     * The number `[from, until)` spells, or -1 when it is not digits the whole way or is
     * longer than a Long holds. Leading zeros read as the number they spell, so "06" sits
     * five away from "11".
     */
    fun numberWithin(from: Int, until: Int): Long {
        val start = from.coerceAtLeast(0)
        val end = minOf(until, size)
        if (start >= end || end - start > MAX_DIGITS) return -1

        var value = 0L
        for (index in start until end) {
            if (!raw[index].isDigit()) return -1
            value = value * 10 + (raw[index] - '0')
        }
        return value
    }

    /**
     * The whole number the digits in `[from, until)` belong to, or -1 when the range holds
     * no digits or holds parts of two different numbers.
     *
     * Reaching outside the range is the point. What separates two passwords is rarely a
     * whole number: "Summer2023!" and "Summer2024!" disagree about one character of one,
     * and the question worth asking is what 2023 and 2024 are to each other, not what 3
     * and 4 are.
     */
    fun numberAround(from: Int, until: Int): Long {
        val start = from.coerceAtLeast(0)
        val end = minOf(until, size)
        var at = start
        while (at < end && !raw[at].isDigit()) at++
        if (at >= end) return -1

        var runStart = at
        while (runStart > 0 && raw[runStart - 1].isDigit()) runStart--
        var runEnd = at
        while (runEnd < size && raw[runEnd].isDigit()) runEnd++

        // A range straddling two numbers says nothing about either of them.
        for (index in runEnd until end) if (raw[index].isDigit()) return -1

        return numberWithin(runStart, runEnd)
    }

    /**
     * Whether a run begins at [index], so a boundary drawn here does not cut through the
     * middle of a word or a number. The ends of the password count as boundaries.
     */
    fun startsRun(index: Int): Boolean =
        index <= 0 || index >= size || slotStart[index] == index

    /**
     * Whether `[from, until)` stays inside one run, and that run is not a word.
     *
     * Bumping a year, a counter or a symbol moves a single slot and leaves everything else
     * where it was. Two slots moving at once is two independent choices, which is what
     * unrelated passwords off the same template look like: "Vanilla2000+" and
     * "Vanilla2007#" agree on the word and on nothing else.
     *
     * A word never counts, however alone it stands. Trading one word for another is
     * trading the whole of what an attacker would have had to guess, so "garden22" and
     * "harden22" are not one password with an edit in it.
     */
    fun movesOneSlot(from: Int, until: Int): Boolean {
        val end = minOf(until, size)
        if (from >= end) return true
        if (kind[from] == WORD) return false

        val slot = slotStart[from]
        for (index in from until end) if (slotStart[index] != slot) return false
        return true
    }

    fun wipe() = canonical.fill(' ')

    private fun countBetween(running: IntArray, from: Int, until: Int) =
        running[until.coerceAtMost(size)] - running[from.coerceAtLeast(0)]

    private fun isSecret(index: Int) = kind[index] == SECRET || kind[index] == SEPARATOR

    private fun isWordlike(index: Int) = kind[index] == WORD || kind[index] == SEPARATOR

    private fun classify(starts: ArrayList<Int>, ends: ArrayList<Int>) {
        var index = 0
        while (index < size) {
            if (index + YEAR_LENGTH <= size && isYearAt(index)) {
                mark(FREE, index, index + YEAR_LENGTH)
                index += YEAR_LENGTH
                continue
            }

            val word = wordAt(index)
            if (word > 0) {
                mark(WORD, index, index + word)
                starts += index
                ends += index + word
                index += word
                continue
            }

            val digits = digitRunAt(index)
            if (digits > 0) {
                // Past the whole run either way, so a long one cannot be discounted a few
                // digits at a time from its tail.
                val free = digits <= SHORT_DIGIT_RUN || countsOrRepeats(index, index + digits)
                mark(if (free) FREE else SECRET, index, index + digits)
                index += digits
                continue
            }

            // A symbol stands alone: two of them side by side are two choices, not one.
            mark(if (raw[index] in WORD_SEPARATORS) SEPARATOR else SECRET, index, index + 1)
            index++
        }
    }

    private fun mark(value: Byte, from: Int, until: Int) {
        kind.fill(value, from, until)
        for (index in from until until) slotStart[index] = from
    }

    /**
     * Length of the word starting at [index]: shape first, the model only for what shape
     * cannot read.
     *
     * Shape comes first because it knows a capital starts something. The model scores
     * whatever run of letters it is handed, and in "SummerTime" that run is ten letters
     * and one word where a person wrote two. Losing the boundary loses the count, and two
     * shared words is evidence a pair is one password edited where one shared word is not.
     *
     * The model's job is the spellings shape was never able to read: a word that is
     * shouted, leeted, or capitalised in the middle. Shape keeps the rest, because plenty
     * of what a person writes into a password is in no dictionary and is still no secret,
     * like a site name or a surname, and those are written the way words are.
     */
    private fun wordAt(index: Int): Int {
        val start = if (raw[index].isUpperCase()) index + 1 else index
        var end = start
        while (end < size && raw[end].isLowerCase()) end++
        val run = if (end == start) 0 else end - index
        if (run >= WORD_RUN) return run

        return WordModel.wordRunAt(canonical, raw, index, size)
    }

    /**
     * Whether `[from, until)` counts up, counts down, or repeats one digit.
     *
     * "12345678" is as long as a number gets and costs an attacker nothing, and so does
     * "87654321" and "000000". Treating a run that long as a secret purely because of its
     * length reported two weak passwords as a pair whenever they happened to start
     * counting from the same place, which was a seventh of every false pair left.
     */
    private fun countsOrRepeats(from: Int, until: Int): Boolean {
        val step = raw[from + 1] - raw[from]
        if (step != 0 && step != 1 && step != -1) return false

        for (index in from + 1 until until) if (raw[index] - raw[index - 1] != step) return false
        return true
    }

    private fun digitRunAt(index: Int): Int {
        var end = index
        while (end < size && raw[end].isDigit()) end++
        return end - index
    }

    private fun isYearAt(index: Int): Boolean {
        val leads = raw[index] == '1' && raw[index + 1] == '9' ||
                raw[index] == '2' && raw[index + 1] == '0'
        return leads && raw[index + 2].isDigit() && raw[index + 3].isDigit()
    }

    private companion object {
        const val SECRET: Byte = 0
        const val FREE: Byte = 1
        const val WORD: Byte = 2
        const val SEPARATOR: Byte = 3

        /** Digits a Long still holds. Past this the value is not worth reading. */
        const val MAX_DIGITS = 18
    }
}

internal fun commonPrefixLength(first: CharArray, second: CharArray): Int {
    val limit = minOf(first.size, second.size)
    var length = 0
    while (length < limit && first[length] == second[length]) length++
    return length
}

internal fun commonSuffixLength(first: CharArray, second: CharArray): Int {
    val limit = minOf(first.size, second.size)
    var length = 0
    while (length < limit && first[first.size - 1 - length] == second[second.size - 1 - length])
        length++
    return length
}

/**
 * Optimal string alignment distance, which counts insertions, deletions, substitutions
 * and transpositions of adjacent characters.
 *
 * Gives up as soon as a whole row exceeds [limit] and returns `limit + 1`. Most pairs in
 * a vault are nowhere near each other, so that early exit is what keeps the pairwise
 * sweep affordable.
 *
 * [rows] is three scratch rows, each at least `second.size + 1` long. They are handed in
 * rather than allocated because the sweep runs this once per pair, and three arrays per
 * pair is the bulk of what the check churns through.
 */
internal fun boundedDistance(
    first: CharArray,
    second: CharArray,
    limit: Int,
    rows: DistanceRows,
): Int {
    if (abs(first.size - second.size) > limit) return limit + 1

    var twoRowsAgo = rows.first
    var previousRow = rows.second
    var currentRow = rows.third
    for (j in 0..second.size) previousRow[j] = j

    for (i in 1..first.size) {
        currentRow[0] = i
        var bestInRow = i

        for (j in 1..second.size) {
            val substitution = if (first[i - 1] == second[j - 1]) 0 else 1
            var cost = minOf(
                previousRow[j] + 1,
                currentRow[j - 1] + 1,
                previousRow[j - 1] + substitution,
            )
            if (i > 1 && j > 1 && first[i - 1] == second[j - 2] && first[i - 2] == second[j - 1])
                cost = minOf(cost, twoRowsAgo[j - 2] + 1)

            currentRow[j] = cost
            if (cost < bestInRow) bestInRow = cost
        }

        if (bestInRow > limit) return limit + 1

        val recycled = twoRowsAgo
        twoRowsAgo = previousRow
        previousRow = currentRow
        currentRow = recycled
    }

    return previousRow[second.size]
}

/** Scratch space for [boundedDistance], reused across every pair in one sweep. */
internal class DistanceRows(width: Int) {
    val first = IntArray(width + 1)
    val second = IntArray(width + 1)
    val third = IntArray(width + 1)
}

/** Value-equality wrapper so a char array can key a map. */
internal class CharArrayKey(private val value: CharArray) {
    override fun equals(other: Any?) = other is CharArrayKey && value.contentEquals(other.value)
    override fun hashCode() = value.contentHashCode()
}
