package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.feature.password_health.domain.checker.WordModel.WORTH_IT
import me.gosimple.nbvcxz.resources.DictionaryUtil
import kotlin.math.ln

/**
 * Whether a run of letters reads like a word, which is the same question as whether an
 * attacker gets it for free.
 *
 * A word list answers this in one language only. A German, French or Spanish word
 * shouted or leeted past a shape rule is not on an English list, so the people whose
 * passwords are not written in English are exactly the people the list does not help,
 * and carrying a list per language does not scale.
 *
 * So instead: words are not random letters. "ratte" and "rttae" use the same five
 * letters and only one could be a word, because languages that share an alphabet share a
 * great deal of how letters follow one another. An order 3 Markov model counts, for
 * every pair of letters, which letter came next, and reads a run by how much less of a
 * surprise it is than the same many letters drawn at random. Trained on English alone it
 * still reads "kartoffel", "papillon" and "mariposa" as words and "b7Kqxz" as a secret,
 * because what it learned is mostly not English but the shape of European spelling.
 *
 * Training uses the nbvcxz dictionary already on the classpath, so this costs no asset
 * and no download: 19,683 bytes in place of 22,160 hashes.
 *
 * Scoring takes a range of an already canonical array rather than a string. Building
 * strings out of password fragments would leave copies behind that cannot be wiped.
 */
internal object WordModel {

    /**
     * Shorter than this and a "word" is a fragment: at two characters a random token can
     * be nibbled away a syllable at a time until nothing is left to crack. Three is as low
     * as it can go, because passphrase wordlists are full of three letter words and
     * "Oak-Elk-Ivy-Fig-Owl" has to read as five of them.
     */
    const val MIN_WORD = 3

    /**
     * Length of the word starting at [from], or 0.
     *
     * Where the word ends has to be worked out, not assumed, because canonicalising merges
     * a word with whatever follows it: "H@rb0r1993+" folds to eleven letters in a row.
     * Asking whether those eleven are a word only ever answers no.
     *
     * So every prefix is weighed against chance. Each character earns the model's opinion
     * of it less what a letter drawn at random would have cost, and the word is the prefix
     * where that running total peaks: "harbor" peaks at six and every letter of "igget"
     * after it takes the total back down. An average would not do this, because six
     * confident characters carry five unlikely ones and the whole of "harborigget" reads
     * as fine on average.
     *
     * A run that never gets [WORTH_IT] ahead of chance is not a word at all.
     *
     * The run is taken from [canonical], so a leeted spelling is one run and not three.
     * [raw] only has to show the run was not digits that happened to fold into letters.
     */
    fun wordRunAt(canonical: CharArray, raw: CharArray, from: Int, until: Int): Int {
        var end = from
        while (end < until && canonical[end].isLetter()) end++

        val run = end - from
        if (run < MIN_WORD) return 0

        var back2 = EDGE
        var back1 = EDGE
        var running = 0.0
        var readable = 0
        var sawLetter = false
        var best = WORTH_IT
        var word = 0

        for (index in from until end) {
            // A run of digits can fold into letters by accident: "1337" canonicalises to
            // "ieet". One real letter is enough to tell a respelled word from that.
            if (raw[index].isLetter()) sawLetter = true

            val symbol = symbolOf(canonical[index])
            if (symbol < 0) {
                // A letter the model never saw is not evidence of anything. Pick the word
                // up again on the far side of it rather than score a surprise.
                back2 = EDGE
                back1 = EDGE
                continue
            }

            running += logProbability(back2, back1, symbol) - BY_CHANCE
            if (!raw[index].isLetter()) running -= RESPELLING
            readable++
            back2 = back1
            back1 = symbol

            val length = index - from + 1
            if (length < MIN_WORD || readable < MIN_WORD || !sawLetter) continue

            // A word can be spelled with digits in it and does not end in one. Every
            // character of "1993+" folds to a letter, so "harborigget" reads as a run of
            // eleven, and the only thing that still says where "harbor" stopped is that
            // the characters after it were never letters to begin with.
            if (!raw[index].isLetter()) continue

            // Where a word stops is as telling as how it runs. Stopping after "harbor" is
            // ordinary and stopping after "harborig" is not, so the closing edge is
            // charged for like any other character.
            val whole = running + logProbability(back2, back1, EDGE) - BY_CHANCE
            if (whole > best) {
                best = whole
                word = length
            } else if (best - whole > GIVE_UP) {
                // The word is over and the rest of the run belongs to something else.
                // Stopping matters because a later stretch can read well on its own
                // account and drag the word across everything in between: "qwertyuiop" is
                // two readable halves and an unreadable join. The caller asks again here.
                break
            }
        }

        if (!sawLetter) return 0

        // Letters in an alphabet the model was never shown. Letters in a row inside a
        // password are a word far more often than they are a secret, and refusing to read
        // them would put every language outside this one back where the English list left
        // them.
        if (readable < MIN_WORD) return run

        // What is left over is too short to be a word of its own, so it is the tail of
        // this one. "CEDAR" reads best as "ced", since a great many words end in "ed", and
        // leaving "ar" behind charges an attacker for two characters nobody chose
        // separately. Only letters are taken this way: the leftover of "Garden@" is a
        // respelling and belongs to whatever comes after it.
        if (word > 0 && run - word < MIN_WORD && allLetters(raw, from + word, from + run))
            return run

        return word
    }

    private fun allLetters(raw: CharArray, from: Int, until: Int): Boolean {
        for (index in from until until) if (!raw[index].isLetter()) return false
        return true
    }

    private fun symbolOf(char: Char) = if (char in 'a'..'z') char - 'a' else -1

    private fun logProbability(back2: Int, back1: Int, next: Int): Double =
        -(table[(back2 * ALPHABET + back1) * ALPHABET + next].toInt() and 0xFF) / SCALE

    private val table: ByteArray by lazy { train() }

    /**
     * Counts every letter that followed every pair of letters, then stores the result as
     * one byte of surprise per cell. A byte is 1/16th of a nat apart from its neighbour
     * and the totals are compared at a distance of whole nats, so the rounding never
     * decides anything.
     */
    private fun train(): ByteArray {
        val counts = IntArray(CELLS)
        val totals = IntArray(CONTEXTS)

        // english is ordinary vocabulary, eff_large the list passphrase generators draw
        // from. Names, surnames and the leaked password list are deliberately left out: a
        // model taught that "qwerty" is ordinary would stop charging for it.
        listOf(DictionaryUtil.english, DictionaryUtil.eff_large).forEach { name ->
            DictionaryUtil.loadUnrankedDictionary(name).keys.forEach { word ->
                val trimmed = word.trim().lowercase()
                if (trimmed.length >= MIN_WORD && trimmed.all { it in 'a'..'z' })
                // Trained canonicalised, because that is the form a lookup arrives in.
                // Skip this and no word holding an l reads as one: "maple"
                // canonicalises to "mapie", since l, 1, | and ! all read as i.
                    learn(trimmed.toCharArray().canonicalize(), counts, totals)
            }
        }

        val table = ByteArray(CELLS)
        for (context in 0 until CONTEXTS) {
            val total = totals[context] + ALPHABET * SMOOTHING
            for (next in 0 until ALPHABET) {
                val cell = context * ALPHABET + next
                val probability = (counts[cell] + SMOOTHING) / total
                table[cell] = (-ln(probability) * SCALE).toInt().coerceIn(0, 255).toByte()
            }
        }
        return table
    }

    private fun learn(word: CharArray, counts: IntArray, totals: IntArray) {
        var back2 = EDGE
        var back1 = EDGE
        word.forEach { char ->
            val symbol = symbolOf(char)
            val context = back2 * ALPHABET + back1
            counts[context * ALPHABET + symbol]++
            totals[context]++
            back2 = back1
            back1 = symbol
        }
        val context = back2 * ALPHABET + back1
        counts[context * ALPHABET + EDGE]++
        totals[context]++
    }

    /** 26 letters and the symbol standing for the edge of a word. */
    private const val ALPHABET = 27
    private const val EDGE = 26
    private const val CONTEXTS = ALPHABET * ALPHABET
    private const val CELLS = CONTEXTS * ALPHABET

    /**
     * What every letter is credited with having followed every pair, so that nothing is
     * impossible and a word built of unseen pairs is merely very expensive.
     *
     * Deliberately tiny. A letter the training set never put after a pair it has seen
     * thousands of times then costs around eleven nats, and that is what separates a token
     * from a word: a token is mostly ordinary letters in an order no word takes. Making it
     * larger flattens exactly the distinction the model exists to draw.
     */
    private const val SMOOTHING = 0.01

    private const val SCALE = 16.0

    /**
     * What one character of a word would have cost had it been drawn at random, which is
     * what every character of a candidate word is measured against.
     *
     * Seventy, not twenty six, because what a letter of a password competes against is not
     * another letter: it is a digit or a symbol that canonicalising turned into one. An
     * ordinary character of a word beats this by around two nats.
     */
    private val BY_CHANCE = ln(1.0 / 70)

    /**
     * What a character costs a word for having been written as something other than a
     * letter.
     *
     * People do write "Dr@gon", and six ordinary characters earn enough to carry one
     * respelling, so it still reads as a word. What does not survive is a word reaching
     * across a respelling into another one: "Uzb+audible+Uzb" folds to a single run of
     * letters and only the crossings say it is three things.
     */
    private const val RESPELLING = 6.0

    /**
     * How far the reading may fall below its best before the word is taken to have ended.
     *
     * Wide, because the reading at a given length asks whether a word ends there, and
     * inside a word the honest answer is no: nothing ends in "ceda", so a narrow margin
     * would stop before reaching "cedar". What it still catches is a run that has gone well
     * past anything a word does.
     */
    private const val GIVE_UP = 6.0

    /**
     * How far ahead of chance a run has to read before it is worth calling a word.
     *
     * A model says yes to anything spelled the way words are spelled, and at three
     * characters a great many tokens are: "sparkasse-M4H62C" would give up "mah" and
     * "discord EwBiH" would give up "ewbi", and what is left is no longer enough to tell
     * two tokens apart. Asking for a real margin rather than a longer run is what keeps
     * "oak" and "elk" while turning those down.
     */
    private const val WORTH_IT = 4.0
}
