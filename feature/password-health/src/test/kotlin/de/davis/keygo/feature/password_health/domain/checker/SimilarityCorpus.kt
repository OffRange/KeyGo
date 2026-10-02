package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.password_health.domain.model.PasswordCandidate
import java.util.UUID
import kotlin.random.Random

/**
 * Labelled password corpora for measuring [SimilarPasswordChecker].
 *
 * The label that matters is [CorpusEntry.family]: two entries share a family when one
 * was built from the other by a transformation that keeps the unpredictable part of
 * the password, so cracking one hands you the other. Everything else is a negative,
 * including entries that share only public or predictable structure -- a year, a site
 * name, a dictionary word, a habitual symbol suffix.
 *
 * Two shapes are generated. [vaults] models what the checker actually runs against: a
 * person's vault, where a small personal vocabulary makes unrelated passwords look
 * alike. [singleVault] is one big pile with a wide vocabulary, used for timing.
 */
internal data class CorpusEntry(
    val id: ItemId,
    val password: String,
    /** Shared secret material. Negative values are unique to the entry. */
    val family: Int,
    val archetype: String,
    /** How this entry was derived: "base", a variant name, "duplicate" or "independent". */
    val relation: String,
    /** Shared surface structure, for attributing false positives. */
    val cluster: String,
    /** Transformation steps away from the family base. */
    val distanceFromBase: Int,
)

/** Unordered pair of item ids. */
internal data class PairKey(val a: ItemId, val b: ItemId) {
    companion object {
        fun of(first: ItemId, second: ItemId): PairKey =
            if (first < second) PairKey(first, second) else PairKey(second, first)
    }
}

internal class Corpus(val entries: List<CorpusEntry>) {

    val byId: Map<ItemId, CorpusEntry> = entries.associateBy { it.id }

    /** Pairs the checker is expected to flag: same secret material, different text. */
    val variantPairs: Set<PairKey>

    /** Same secret material and identical text. Out of scope, ReusePasswordCheck owns these. */
    val duplicatePairs: Set<PairKey>

    init {
        val groups = UnionFind(entries.size)

        entries.indices.filter { entries[it].family >= 0 }
            .groupBy { entries[it].family }
            .values
            .forEach { members -> members.forEach { groups.union(members.first(), it) } }

        joinNearIdenticalText(groups)

        val variants = mutableSetOf<PairKey>()
        val duplicates = mutableSetOf<PairKey>()
        entries.indices.groupBy { groups.find(it) }.values.forEach { component ->
            for (i in component.indices) for (j in i + 1..component.lastIndex) {
                val first = entries[component[i]]
                val second = entries[component[j]]
                val key = PairKey.of(first.id, second.id)
                if (first.password == second.password) duplicates += key else variants += key
            }
        }
        variantPairs = variants
        duplicatePairs = duplicates
    }

    /**
     * Joins entries the generator drew separately but that a cracker would hand over
     * together anyway.
     *
     * Whether two passwords are related is a property of the passwords, not of how they
     * came to exist. The generator works from a small vocabulary on purpose, and small
     * vocabularies collide: sixteen keyboard walks and eleven symbols cannot make many
     * distinct passwords. Labelling those unrelated marks a checker wrong for finding a
     * pair that really does fall together, and at one point that single artifact was 39%
     * of every false pair measured.
     *
     * One edit is the whole allowance, deliberately. Two leaves room for a coincidence a
     * person could have arrived at twice ("Phoenix*10" beside "Phoenix*92"), and those
     * stay negatives, which is what keeps this from quietly agreeing with whatever the
     * checker happens to do.
     */
    private fun joinNearIdenticalText(groups: UnionFind) {
        val canonical = entries.map { it.password.toCharArray().canonicalize() }
        val rows = DistanceRows(canonical.maxOfOrNull { it.size } ?: return)
        val byLength = canonical.indices.groupBy { canonical[it].size }

        canonical.indices.forEach { index ->
            for (length in canonical[index].size..canonical[index].size + NEAR_EDITS)
                byLength[length].orEmpty().forEach { other ->
                    if (other > index && groups.find(other) != groups.find(index) &&
                        boundedDistance(
                            canonical[index],
                            canonical[other],
                            NEAR_EDITS,
                            rows,
                        ) <= NEAR_EDITS
                    ) groups.union(index, other)
                }
        }
    }

    private class UnionFind(size: Int) {
        private val parent = IntArray(size) { it }

        fun find(node: Int): Int {
            var root = node
            while (parent[root] != root) root = parent[root]
            var walk = node
            while (parent[walk] != root) {
                val next = parent[walk]
                parent[walk] = root
                walk = next
            }
            return root
        }

        fun union(first: Int, second: Int) {
            parent[find(first)] = find(second)
        }
    }

    private companion object {
        /** Edits at which one password plainly gives the other away, however it was written. */
        const val NEAR_EDITS = 1
    }

    fun candidates(): List<PasswordCandidate> = entries.map {
        PasswordCandidate(
            id = it.id,
            score = PasswordScore.Moderate,
            password = it.password.toCharArray(),
        )
    }
}

internal object SimilarityCorpus {

    /** Site names the corpus uses, so a report can tell a public token from a secret one. */
    val siteNames: List<String> get() = SITES

    /** Every word the corpus builds passwords from, for the same reason. */
    val vocabulary: Set<String> by lazy {
        (WORDS + PSEUDO_WORDS + COMMON_WEAK + KEYBOARD_WALKS + SITES).toSet()
    }

    /** A wordlist to draw passphrases from, of the order a real one has. */
    val passphraseWords: List<String> get() = WORDS + PSEUDO_WORDS

    /**
     * A vault of [size] entries built the way one person builds passwords: a handful of
     * favourite words, the accounts they actually hold, and their own habits about how
     * much they reuse.
     */
    fun vault(size: Int, random: Random, vocabularyScale: Double = 1.0): Corpus {
        val vocabulary = personalVocabulary(random, vocabularyScale)
        val accounts = SITES.shuffled(random).take(random.nextInt(8, 24))
        return CorpusBuilder(
            random = random,
            words = vocabulary,
            sites = accounts,
            variantShare = random.nextDouble(0.05, 0.45),
            duplicateShare = random.nextDouble(0.0, 0.15),
            hardNegativeShare = 0.10,
        ).build(size)
    }

    /**
     * [vocabularyScale] widens the personal vocabulary: 1.0 is a person with a dozen
     * favourite words, 4.0 someone who rarely repeats one. It is the single assumption
     * the false positive rate is most sensitive to, so it is a knob rather than a
     * constant.
     */
    fun vaults(
        count: Int,
        seed: Long = 20260919L,
        sizes: IntRange = 20..400,
        vocabularyScale: Double = 1.0,
    ): List<Corpus> {
        val random = Random(seed)
        return List(count) {
            // Small vaults are the common case, so skew towards the low end.
            val size = minOf(
                sizes.last,
                sizes.first + (random.nextDouble() * random.nextDouble() *
                        (sizes.last - sizes.first)).toInt(),
            )
            vault(size, random, vocabularyScale)
        }
    }

    /** One undifferentiated pile, for timing the pairwise sweep. */
    fun singleVault(size: Int, seed: Long = 20260919L): Corpus {
        val random = Random(seed)
        return CorpusBuilder(
            random = random,
            words = WORDS + PSEUDO_WORDS,
            sites = SITES,
            variantShare = 0.24,
            duplicateShare = 0.05,
            hardNegativeShare = 0.14,
        ).build(size)
    }

    private fun personalVocabulary(random: Random, scale: Double): List<String> {
        fun widen(low: Int, high: Int) = random.nextInt(
            (low * scale).toInt().coerceAtLeast(1),
            (high * scale).toInt().coerceAtLeast(2),
        )

        val favourites = WORDS.shuffled(random).take(widen(6, 13))
        val rest = PSEUDO_WORDS.shuffled(random).take(widen(4, 10))
        // Favourites are picked more often than the rest, the way a habit works.
        return favourites + favourites + rest
    }

    // region vocabulary

    private val WORDS = listOf(
        "summer", "winter", "spring", "autumn", "dragon", "monkey", "sunshine", "princess",
        "football", "baseball", "mustang", "shadow", "master", "jordan", "harley", "ranger",
        "hunter", "buster", "soccer", "hockey", "thunder", "falcon", "phoenix", "marmot",
        "thistle", "garden", "canyon", "lantern", "puzzle", "otter", "badger", "walnut",
        "copper", "silver", "golden", "purple", "orange", "violet", "indigo", "crimson",
        "amber", "jasper", "quartz", "granite", "marble", "cobalt", "nickel", "carbon",
        "oxygen", "helium", "argon", "kepler", "orion", "sirius", "nebula", "comet",
        "meteor", "galaxy", "cosmos", "saturn", "jupiter", "mercury", "neptune", "titan",
        "europa", "rocket", "anchor", "harbor", "compass", "seagull", "dolphin", "narwhal",
        "penguin", "walrus", "tiger", "leopard", "cheetah", "panther", "jaguar", "cougar",
        "bobcat", "coyote", "raven", "sparrow", "heron", "egret", "pelican", "osprey",
        "kestrel", "condor", "vulture", "crane", "stork", "maple", "cedar", "birch",
        "aspen", "willow", "poplar", "spruce", "juniper", "laurel", "myrtle", "hazel",
        "alder", "rowan", "hawthorn", "bramble", "clover", "meadow", "prairie", "tundra",
        "glacier", "summit", "ridge", "hollow", "boulder", "pebble", "cinder", "ember",
        "kindle", "beacon", "mantle", "keystone", "archway", "cellar", "attic",
        "bureau", "satchel", "kettle", "skillet", "saucer", "goblet", "tankard", "flagon",
        "banner", "pennant", "cobble", "gravel", "thicket", "bracken", "heather", "gorse",
        "nettle", "sorrel", "fennel", "parsley", "saffron", "cumin", "pepper", "ginger",
        "cinnamon", "vanilla", "almond", "cashew", "pecan", "chestnut", "acorn", "cypress",
    )

    /**
     * Pronounceable filler so a large corpus does not force unrelated passwords to share
     * a word just because the word list ran out. They behave like real words everywhere
     * the checker looks: all lowercase letters, spelled the way [WordModel] expects.
     */
    private val PSEUDO_WORDS: List<String> = buildSet {
        val random = Random(7L)
        val onsets = listOf(
            "b", "c", "d", "f", "g", "h", "j", "k", "l", "m", "n", "p", "r", "s", "t", "v",
            "w", "z", "br", "cr", "dr", "fl", "gl", "pl", "sh", "st", "tr", "cl", "sp", "sn",
        )
        val nuclei = listOf("a", "e", "i", "o", "u", "ai", "ea", "ee", "oo", "ou", "au", "ie")
        val codas = listOf("", "n", "r", "l", "s", "t", "m", "k", "d", "ng", "rd", "ll", "nt")
        while (size < 4_000) {
            val word = (1..random.nextInt(2, 4)).joinToString("") {
                onsets.random(random) + nuclei.random(random) + codas.random(random)
            }
            if (word.length in 4..11) add(word)
        }
    }.toList()

    private val SITES = listOf(
        "facebook", "twitter", "google", "amazon", "netflix", "spotify", "github", "gitlab",
        "reddit", "linkedin", "instagram", "tiktok", "dropbox", "slack", "discord", "steam",
        "paypal", "stripe", "chase", "barclays", "revolut", "sparkasse", "comdirect", "ebay",
        "etsy", "shopify", "zalando", "mediamarkt", "lidl", "rewe", "booking", "airbnb",
        "expedia", "ryanair", "lufthansa", "bahn", "uber", "doordash", "deliveroo", "audible",
        "kindle", "hulu", "disney", "twitch", "youtube", "vimeo", "notion", "figma",
        "atlassian", "cloudflare", "namecheap", "hetzner", "digitalocean", "protonmail",
    )

    private val COMMON_WEAK = listOf(
        "password", "password1", "password123", "123456", "1234567", "12345678", "123456789",
        "qwerty", "qwerty123", "letmein", "iloveyou", "welcome", "monkey123", "abc123",
        "trustno1", "dragon123", "sunshine1", "princess1", "football1", "starwars",
        "whatever", "changeme", "admin123", "passw0rd", "qazwsx", "zaq12wsx", "michael1",
        "superman1", "batman123", "pokemon1", "minecraft", "liverpool", "arsenal1",
        "chelsea1", "barcelona", "juventus", "ferrari1", "porsche911", "corvette",
    )

    private val KEYBOARD_WALKS = listOf(
        "qwertyuiop", "asdfghjkl", "zxcvbnm123", "1qaz2wsx", "2wsx3edc", "3edc4rfv",
        "qazwsxedc", "1q2w3e4r", "2w3e4r5t", "qwerasdf", "asdfzxcv", "poiuytrewq",
        "mnbvcxz123", "1234qwer", "4rfv5tgb", "zaqxswcde",
    )

    private val SYMBOLS = listOf('!', '@', '#', '%', '&', '*', '?', '-', '_', '+', '$')
    private val SEPARATORS = listOf('-', '_', '.', ' ')
    private const val TOKEN_ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val STRONG_ALPHABET = TOKEN_ALPHABET + "!@#%&*?-_+$"

    private val LEET_OUT = mapOf(
        'a' to '@', 'o' to '0', 'e' to '3', 's' to '$',
        'i' to '1', 't' to '7', 'b' to '8', 'g' to '9',
    )

    private val YEAR_PATTERN = Regex("(19|20)\\d{2}")

    private enum class Archetype(val label: String, val weight: Int) {
        WordYearSymbol("word+year+symbol", 12),
        WordSymbolDigits("word+symbol+digits", 10),
        TwoWordSeparated("two-word", 12),
        SitePrefixToken("site-prefix+token", 10),
        TokenSiteToken("token+site+token", 5),
        CommonWeak("common-weak", 5),
        RandomStrong("random-strong", 20),
        Passphrase("passphrase", 12),
        LeetWordYear("leet word+year", 9),
        KeyboardWalk("keyboard-walk", 5),
    }

    private val ARCHETYPE_POOL = Archetype.entries.flatMap { a -> List(a.weight) { a } }

    /**
     * Every transformation here keeps the part of the password an attacker cannot guess,
     * so the result belongs in the same family as its input.
     */
    private enum class VariantKind(val label: String) {
        Leet("leet"),
        CaseFlip("case-flip"),
        YearBump("year-bump"),
        CounterBump("counter-bump"),
        SymbolSwap("symbol-swap"),
        SiteSwap("site-swap"),
        Typo("typo"),
        AppendToken("append-token"),
    }

    private enum class HardNegative(val label: String) {
        /** Different words, same year and symbol. The year is all they share. */
        SharedYear("shared-year"),

        /** Two accounts on one site, unrelated secrets. */
        SameSiteDifferentSecret("same-site-different-secret"),

        /** Passphrases overlapping in one word out of four. */
        SharedPassphraseWord("shared-passphrase-word"),

        /** Unrelated bases behind the same habitual suffix. */
        SharedSuffix("shared-suffix"),

        /** The same favourite word inside different templates. */
        SharedWord("shared-word"),

        /** Different walks across the same keyboard. */
        KeyboardNeighbours("keyboard-neighbours"),
    }

    // endregion

    private class CorpusBuilder(
        private val random: Random,
        private val words: List<String>,
        private val sites: List<String>,
        private val variantShare: Double,
        private val duplicateShare: Double,
        private val hardNegativeShare: Double,
    ) {

        private val seen = HashSet<String>()
        private val entries = ArrayList<CorpusEntry>()
        private var family = 0

        fun build(size: Int): Corpus {
            var produced = 0
            while (produced < size * variantShare) produced += emitFamily()

            produced = 0
            while (produced < size * duplicateShare && entries.size < size) {
                val (base, archetype) = fresh()
                val id = family++
                emit(base, id, archetype, "base", "family-$id")
                emit(base, id, archetype, "duplicate", "family-$id")
                produced += 2
            }

            produced = 0
            while (produced < size * hardNegativeShare && entries.size < size) {
                val cluster = HardNegative.entries.random(random)
                hardNegative(cluster).forEach { (archetype, password) ->
                    emit(password, unique(), archetype, "independent", cluster.label)
                    produced++
                }
            }

            while (entries.size < size) {
                val (password, archetype) = fresh()
                emit(password, unique(), archetype, "independent", "none")
            }

            return Corpus(entries.take(size).shuffled(random))
        }

        private fun emitFamily(): Int {
            val (base, archetype) = fresh()
            val id = family++
            emit(base, id, archetype, "base", "family-$id")
            var produced = 1

            // A family is either one base with several rewrites, or a password that
            // evolved step by step over the years.
            val chained = random.nextInt(10) < 3
            var current = base
            var steps = 0
            repeat(random.nextInt(1, 4)) {
                val from = if (chained) current else base
                val variant = applyVariant(from) ?: return@repeat
                steps = if (chained) steps + 1 else 1
                emit(variant.second, id, archetype, variant.first, "family-$id", steps)
                current = variant.second
                produced++
            }
            return produced
        }

        private fun unique() = -(entries.size + 1)

        private fun emit(
            password: String,
            family: Int,
            archetype: String,
            relation: String,
            cluster: String,
            distanceFromBase: Int = 0,
        ) {
            entries += CorpusEntry(
                id = UUID(0L, entries.size.toLong()),
                password = password,
                family = family,
                archetype = archetype,
                relation = relation,
                cluster = cluster,
                distanceFromBase = distanceFromBase,
            )
        }

        private fun fresh(): Pair<String, String> {
            repeat(64) {
                val archetype = ARCHETYPE_POOL.random(random)
                val password = build(archetype)
                if (seen.add(password)) return password to archetype.label
            }
            val fallback = build(Archetype.RandomStrong)
            seen.add(fallback)
            return fallback to Archetype.RandomStrong.label
        }

        private fun word() = words.random(random)

        private fun capitalised() = word().replaceFirstChar { it.uppercase() }

        private fun year() = random.nextInt(1990, 2027).toString()

        private fun token(length: Int, alphabet: String = TOKEN_ALPHABET) =
            String(CharArray(length) { alphabet[random.nextInt(alphabet.length)] })

        private fun leet(text: String, count: Int): String {
            val positions = text.indices.filter { text[it].lowercaseChar() in LEET_OUT }
            if (positions.isEmpty()) return text
            val chars = text.toCharArray()
            positions.shuffled(random).take(count).forEach {
                chars[it] = LEET_OUT.getValue(chars[it].lowercaseChar())
            }
            return String(chars)
        }

        private fun build(archetype: Archetype): String = when (archetype) {
            Archetype.WordYearSymbol -> capitalised() + year() + SYMBOLS.random(random)

            Archetype.WordSymbolDigits ->
                capitalised() + SYMBOLS.random(random) + random.nextInt(10, 100)

            Archetype.TwoWordSeparated -> {
                val separator = SEPARATORS.random(random)
                word() + separator + word() + separator + random.nextInt(10, 100)
            }

            Archetype.SitePrefixToken ->
                sites.random(random) + SEPARATORS.random(random) + token(random.nextInt(4, 7))

            Archetype.TokenSiteToken -> {
                val token = token(3)
                val symbol = SYMBOLS.random(random)
                "$token$symbol${sites.random(random)}$symbol$token"
            }

            Archetype.CommonWeak -> COMMON_WEAK.random(random)

            Archetype.RandomStrong -> token(random.nextInt(16, 23), STRONG_ALPHABET)

            Archetype.Passphrase -> {
                val separator = SEPARATORS.random(random)
                (1..4).joinToString(separator.toString()) { word() }
            }

            Archetype.LeetWordYear -> leet(
                capitalised() + capitalised() + year() + SYMBOLS.random(random),
                count = random.nextInt(1, 3),
            )

            Archetype.KeyboardWalk -> KEYBOARD_WALKS.random(random) + SYMBOLS.random(random)
        }

        private fun applyVariant(password: String): Pair<String, String>? {
            repeat(16) {
                val kind = VariantKind.entries.random(random)
                val candidate = variant(kind, password)
                if (candidate != null && candidate != password && candidate.length >= 4 &&
                    seen.add(candidate)
                ) return kind.label to candidate
            }
            return null
        }

        private fun variant(kind: VariantKind, password: String): String? = when (kind) {
            VariantKind.Leet -> leet(password, count = random.nextInt(1, 3))

            VariantKind.CaseFlip -> {
                val letters = password.indices.filter { password[it].isLetter() }
                if (letters.isEmpty()) null else {
                    val chars = password.toCharArray()
                    letters.shuffled(random).take(random.nextInt(1, 3)).forEach {
                        chars[it] = if (chars[it].isUpperCase()) chars[it].lowercaseChar()
                        else chars[it].uppercaseChar()
                    }
                    String(chars)
                }
            }

            VariantKind.YearBump -> YEAR_PATTERN.find(password)?.let {
                val bumped = it.value.toInt() + random.nextInt(1, 4)
                password.replaceRange(it.range, bumped.toString())
            }

            VariantKind.CounterBump -> {
                val trailing = password.takeLastWhile { it.isDigit() }
                if (trailing.isEmpty()) password + random.nextInt(1, 10)
                else password.dropLast(trailing.length) + (trailing.toLong() + 1)
            }

            VariantKind.SymbolSwap -> password.lastOrNull()?.takeIf { it in SYMBOLS }?.let { last ->
                password.dropLast(1) + SYMBOLS.filter { it != last }.random(random)
            }

            VariantKind.SiteSwap -> sites.firstOrNull { password.contains(it, ignoreCase = true) }
                ?.let { present ->
                    val others = sites.filter { it != present }
                    if (others.isEmpty()) null else password.replace(present, others.random(random))
                }

            VariantKind.Typo -> {
                val chars = password.toCharArray()
                val at = random.nextInt(chars.size)
                when (random.nextInt(4)) {
                    0 -> password.replaceRange(at, at + 1, TOKEN_ALPHABET.random(random).toString())
                    1 -> password.removeRange(at, at + 1)
                    2 -> StringBuilder(password).insert(at, TOKEN_ALPHABET.random(random))
                        .toString()

                    else -> if (at + 1 < chars.size) {
                        chars[at] = password[at + 1]
                        chars[at + 1] = password[at]
                        String(chars)
                    } else null
                }
            }

            VariantKind.AppendToken -> password + token(random.nextInt(1, 4))
        }

        private fun hardNegative(cluster: HardNegative): List<Pair<String, String>> {
            val members = when (cluster) {
                HardNegative.SharedYear -> {
                    val year = year()
                    val symbol = SYMBOLS.random(random)
                    List(random.nextInt(2, 5)) {
                        "word+year+symbol" to capitalised() + capitalised() + year + symbol
                    }
                }

                HardNegative.SameSiteDifferentSecret -> {
                    val site = sites.random(random)
                    val separator = SEPARATORS.random(random)
                    List(random.nextInt(2, 4)) {
                        "site-prefix+token" to site + separator + token(random.nextInt(4, 7))
                    }
                }

                HardNegative.SharedPassphraseWord -> {
                    val shared = word()
                    val separator = SEPARATORS.random(random)
                    List(2) {
                        "passphrase" to (listOf(shared) + List(3) { word() })
                            .joinToString(separator.toString())
                    }
                }

                HardNegative.SharedSuffix -> {
                    val suffix = "${SYMBOLS.random(random)}${random.nextInt(10, 100)}"
                    List(random.nextInt(2, 4)) {
                        "word+symbol+digits" to capitalised() + word() + suffix
                    }
                }

                HardNegative.SharedWord -> {
                    val shared = word()
                    listOf(
                        "word+year+symbol" to shared.replaceFirstChar { it.uppercase() } +
                                year() + SYMBOLS.random(random),
                        "two-word" to shared + SEPARATORS.random(random) + word() +
                                random.nextInt(10, 100),
                        "site-prefix+token" to sites.random(random) + "_" + shared + token(3),
                    )
                }

                HardNegative.KeyboardNeighbours -> KEYBOARD_WALKS.shuffled(random).take(2).map {
                    "keyboard-walk" to it + SYMBOLS.random(random)
                }
            }
            return members.filter { seen.add(it.second) }
        }
    }
}
