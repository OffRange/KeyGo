package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import kotlinx.coroutines.test.runTest
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours

/**
 * Measures [SimilarPasswordChecker] against labelled corpora: how many variations it
 * finds, how many unrelated passwords it joins, and what the pairwise sweep costs as a
 * vault grows.
 *
 * Not a unit test. Everything is read from the environment so the default run stays cheap:
 *
 *     SIMILARITY_VAULTS=4000 SIMILARITY_POOL_SIZES=5000 \
 *     SIMILARITY_SCALING_SIZES=1000,2000,4000,8000,16000,32000 \
 *       ./gradlew :feature:password-health:testDebugUnitTest \
 *         --tests '*SimilarPasswordCheckerBenchmark*'
 *
 * The report lands in `build/reports/similarity/report.txt`.
 */
class SimilarPasswordCheckerBenchmark {

    private val report = StringBuilder()

    @Test
    fun measure() = runTest(timeout = 6.hours) {
        val vaultCount = env("SIMILARITY_VAULTS")?.toIntOrNull() ?: 300
        val poolSizes = sizes("SIMILARITY_POOL_SIZES", default = listOf(2_000))
        val scalingSizes = sizes("SIMILARITY_SCALING_SIZES", default = listOf(500, 1_000, 2_000))

        line("SimilarPasswordChecker measurement")
        line(
            "java=${System.getProperty("java.version")} cores=${cores()} " +
                    "maxHeap=${Runtime.getRuntime().maxMemory() / (1024 * 1024)}MB"
        )
        line("")

        // Warm the JIT so the first timed run is not measuring the interpreter.
        SimilarPasswordChecker().check(SimilarityCorpus.singleVault(400, seed = 1L).candidates())

        vaultSimulation(vaultCount)
        flush()
        poolSizes.forEach { pool(it); flush() }

        line("== scaling: one vault of n entries ==")
        line(
            "%10s %14s %14s %12s %12s %12s %10s".format(
                "n", "pairs", "elapsed_ms", "pairs/ms", "findings", "alloc_MB", "gc_ms",
            ),
        )
        scalingSizes.forEach { scale(it); flush() }

        println(report)
        println("report written to ${reportFile().absolutePath}")
    }

    private fun reportFile() =
        File(env("SIMILARITY_REPORT_DIR") ?: "build/reports/similarity", "report.txt")

    /** Written after every section so a run that dies late still leaves its results. */
    private fun flush() {
        val out = reportFile()
        out.parentFile?.mkdirs()
        out.writeText(report.toString())
    }

    /**
     * The headline measurement: many separate vaults, each with one person's small
     * vocabulary and habits. Rates are aggregated over every vault, and the per-vault
     * spread says what a single user actually sees.
     */
    private suspend fun vaultSimulation(count: Int) {
        val stats = Stats()
        var elapsed = 0L
        SimilarityCorpus.vaults(count).forEach { corpus ->
            val candidates = corpus.candidates()
            var findings: List<HealthFinding>
            elapsed += measureNanoTime {
                findings = SimilarPasswordChecker().check(candidates).findings
            }
            stats.accumulate(corpus, edgesOf(findings), findings)
        }

        line("== accuracy: $count simulated vaults ==")
        line("passwords            ${stats.items}")
        line(
            "vault size           min ${stats.vaultSizes.min()}, median " +
                    "${stats.vaultSizes.sorted()[stats.vaultSizes.size / 2]}, max ${stats.vaultSizes.max()}"
        )
        line("total elapsed        ${elapsed / 1_000_000} ms")
        line("")
        stats.write()

        val quiet = stats.falsePositivesPerVault.count { it == 0 }
        val sortedFp = stats.falsePositivesPerVault.sorted()
        line("-- what one user sees --")
        line("vaults with no false pair   $quiet / $count  ${pct(ratio(quiet, count))}")
        line(
            "false pairs per vault       median ${sortedFp[sortedFp.size / 2]}, " +
                    "p90 ${sortedFp[(sortedFp.size * 0.9).toInt()]}, max ${sortedFp.last()}"
        )
        val sortedGroups = stats.contaminatedPerVault.sorted()
        line(
            "wrong groups per vault      median ${sortedGroups[sortedGroups.size / 2]}, " +
                    "p90 ${sortedGroups[(sortedGroups.size * 0.9).toInt()]}, max ${sortedGroups.last()}"
        )
        line("")
    }

    /** One undifferentiated pool, for a precision figure that does not depend on vault size. */
    private suspend fun pool(size: Int) {
        val corpus = SimilarityCorpus.singleVault(size)
        val candidates = corpus.candidates()
        val runtime = Runtime.getRuntime()
        System.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        var findings: List<HealthFinding>
        val elapsed =
            measureNanoTime { findings = SimilarPasswordChecker().check(candidates).findings }
        val after = runtime.totalMemory() - runtime.freeMemory()

        val stats = Stats()
        stats.accumulate(corpus, edgesOf(findings), findings)

        line("== accuracy: one pool of $size, wide vocabulary ==")
        line("elapsed              ${elapsed / 1_000_000} ms")
        line("heap delta           ${(after - before) / (1024 * 1024)} MB")
        line("")
        stats.write()
    }

    private suspend fun scale(size: Int) {
        val pairs = size.toLong() * (size - 1) / 2
        try {
            val candidates = SimilarityCorpus.singleVault(size).candidates()
            var findings = 0
            val allocatedBefore = allocatedBytes()
            val gcBefore = gcMillis()
            val elapsed =
                measureNanoTime {
                    findings = SimilarPasswordChecker().check(candidates).findings.size
                }
            val allocated = allocatedBytes() - allocatedBefore
            val gc = gcMillis() - gcBefore
            val millis = elapsed / 1_000_000.0
            line(
                "%10d %14d %14.1f %12.0f %12d %12d %10d".format(
                    size, pairs, millis, pairs / max(millis, 0.001), findings,
                    allocated / (1024 * 1024), gc,
                ),
            )
        } catch (error: OutOfMemoryError) {
            // The findings set holds one entry per flagged pair, so it grows with the
            // square of the vault too. Running out of room is a result, not a mishap.
            line("%10d %14d %14s".format(size, pairs, "OOM: ${error.message}"))
        }
    }

    private fun edgesOf(findings: List<HealthFinding>): Set<PairKey> {
        val edges = HashSet<PairKey>()
        findings.filterIsInstance<HealthFinding.Relation>().forEach { finding ->
            val ids = finding.relatedItemIds.toList()
            for (i in ids.indices) for (j in i + 1..ids.lastIndex)
                edges += PairKey.of(ids[i], ids[j])
        }
        return edges
    }

    // region accumulation

    private inner class Stats {
        var truePositives = 0L
        var falsePositives = 0L
        var falseNegatives = 0L
        var duplicatesFlagged = 0L
        var truePairs = 0L
        var duplicatePairs = 0L
        var scoredPairs = 0L
        var items = 0L
        var comparable = 0L
        var findings = 0L
        var multiMemberFindings = 0L

        var groups = 0L
        var contaminatedGroups = 0L
        var itemsInGroups = 0L
        var largestGroup = 0
        var largestGroupSecrets = 0

        val vaultSizes = mutableListOf<Int>()
        val falsePositivesPerVault = mutableListOf<Int>()
        val contaminatedPerVault = mutableListOf<Int>()

        val recallByVariant = HashMap<String, LongArray>()
        val recallBySteps = HashMap<Int, LongArray>()
        val recallByShape = HashMap<String, LongArray>()
        val causes = HashMap<String, Long>()
        val verdicts = HashMap<String, Long>()
        val examples = HashMap<String, MutableList<String>>()
        val missedExamples = mutableListOf<String>()

        fun accumulate(corpus: Corpus, predicted: Set<PairKey>, raw: List<HealthFinding>) {
            val size = corpus.entries.size
            vaultSizes += size
            items += size
            comparable += corpus.entries.count { it.password.length >= 6 }
            findings += raw.count { it is HealthFinding.Relation }
            multiMemberFindings += raw.filterIsInstance<HealthFinding.Relation>()
                .count { it.relatedItemIds.size > 2 }

            truePairs += corpus.variantPairs.size
            duplicatePairs += corpus.duplicatePairs.size
            scoredPairs += size.toLong() * (size - 1) / 2 - corpus.duplicatePairs.size

            var vaultFalsePositives = 0
            predicted.forEach { pair ->
                when {
                    pair in corpus.variantPairs -> truePositives++
                    pair in corpus.duplicatePairs -> duplicatesFlagged++
                    else -> {
                        falsePositives++
                        vaultFalsePositives++
                        record(corpus, pair)
                    }
                }
            }
            falsePositivesPerVault += vaultFalsePositives

            corpus.variantPairs.forEach { pair ->
                val first = corpus.byId.getValue(pair.a)
                val second = corpus.byId.getValue(pair.b)
                val hit = pair in predicted
                if (!hit) {
                    falseNegatives++
                    if (missedExamples.size < 25) missedExamples +=
                        "%-28s  %-28s  (%s)".format(
                            first.password, second.password,
                            if (first.relation == "base") second.relation else first.relation,
                        )
                }
                val kind = when {
                    first.relation == "base" -> second.relation
                    second.relation == "base" -> first.relation
                    else -> "variant-of-variant"
                }
                recallByVariant.tally(kind, hit)
                recallBySteps.tally(abs(first.distanceFromBase - second.distanceFromBase), hit)
                recallByShape.tally(first.archetype, hit)
            }

            contaminatedPerVault += contamination(corpus, predicted)
        }

        private fun record(corpus: Corpus, pair: PairKey) {
            val first = corpus.byId.getValue(pair.a)
            val second = corpus.byId.getValue(pair.b)
            val cause = cause(first, second)
            val verdict = verdict(first.password, second.password)
            causes[cause] = (causes[cause] ?: 0) + 1
            verdicts[verdict] = (verdicts[verdict] ?: 0) + 1
            val bucket = examples.getOrPut(verdict) { mutableListOf() }
            if (bucket.size < 8) bucket += "[%s] %-28s  %-28s".format(
                cause, first.password, second.password,
            )
        }

        private fun contamination(corpus: Corpus, predicted: Set<PairKey>): Int {
            val parent = HashMap<ItemId, ItemId>()
            fun find(x: ItemId): ItemId {
                var root = parent.getOrPut(x) { x }
                while (root != parent.getValue(root)) root = parent.getValue(root)
                var cursor = x
                while (cursor != root) {
                    val next = parent.getValue(cursor)
                    parent[cursor] = root
                    cursor = next
                }
                return root
            }
            predicted.forEach {
                val a = find(it.a)
                val b = find(it.b)
                if (a != b) parent[a] = b
            }

            val grouped = parent.keys.toList().groupBy(::find)
            var contaminated = 0
            grouped.forEach { (_, members) ->
                val secrets = members.map { corpus.byId.getValue(it).family }.toSet()
                if (secrets.size > 1) contaminated++
                if (members.size > largestGroup) {
                    largestGroup = members.size
                    largestGroupSecrets = secrets.size
                }
            }
            groups += grouped.size
            contaminatedGroups += contaminated
            itemsInGroups += parent.size
            return contaminated
        }

        fun write() {
            val negatives = scoredPairs - truePairs
            val trueNegatives = negatives - falsePositives
            val precision = ratio(truePositives, truePositives + falsePositives)
            val recall = ratio(truePositives, truePairs)
            val f1 =
                if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)

            line("candidates           $items  (comparable, >=6 chars: $comparable)")
            line("true variant pairs   $truePairs")
            line("exact dup pairs      $duplicatePairs  (out of scope, excluded from scoring)")
            line("scored pairs         $scoredPairs  (negatives: $negatives)")
            line("relation findings    $findings  ($multiMemberFindings with more than 2 members)")
            line("")
            line("TP                   $truePositives")
            line("FP                   $falsePositives")
            line("FN                   $falseNegatives")
            line("TN                   $trueNegatives")
            // A copy riding along inside a wider leet family, not a stray reuse edge: the
            // checker never reports a pair of copies on its own. Counted here because
            // expanding a family into pairs makes them look like one.
            line("dup pairs inside families  $duplicatesFlagged")
            line("")
            line("precision            ${pct(precision)}")
            line("recall               ${pct(recall)}")
            line("F1                   ${"%.4f".format(f1)}")
            line("specificity          ${pct(ratio(trueNegatives, negatives))}")
            val perMillion = falsePositives * 1_000_000.0 / max(negatives, 1)
            line("FP per 1M neg pairs  ${"%.1f".format(perMillion)}")
            line("FP per 100 passwords ${"%.2f".format(falsePositives * 100.0 / max(items, 1))}")
            line("")
            line("groups shown         $groups ($itemsInGroups items)")
            val contaminatedShare = pct(ratio(contaminatedGroups, groups))
            line("contaminated groups  $contaminatedGroups  $contaminatedShare")
            line(
                "largest group        $largestGroup items covering " +
                        "$largestGroupSecrets unrelated secrets"
            )
            line("")

            recallByVariant.writeRecall("recall by how the variation was made")
            recallBySteps.writeRecall("recall by edits between the two") { "$it step(s)" }
            recallByShape.writeRecall("recall by password shape")
            line("-- missed examples --")
            missedExamples.take(15).forEach { line("   $it") }
            line("")

            line("-- does the false flag make sense? --")
            verdicts.entries.sortedByDescending { it.value }.forEach { (key, count) ->
                line("%-42s %8d  %s".format(key, count, pct(ratio(count, falsePositives))))
            }
            line("-- what the two passwords shared --")
            causes.entries.sortedByDescending { it.value }.take(16).forEach { (key, count) ->
                line("%-42s %8d  %s".format(key, count, pct(ratio(count, falsePositives))))
            }
            line("-- false positive examples --")
            examples.entries.sortedBy { it.key }.forEach { (verdict, rows) ->
                line("  $verdict")
                rows.forEach { line("     $it") }
            }
            line("")
        }
    }

    private fun <K> HashMap<K, LongArray>.tally(key: K, hit: Boolean) {
        val bucket = getOrPut(key) { LongArray(2) }
        bucket[1]++
        if (hit) bucket[0]++
    }

    /** One `hits/total` table, ordered by its key. */
    private fun <K : Comparable<K>> Map<K, LongArray>.writeRecall(
        title: String,
        name: (K) -> String = { it.toString() },
    ) {
        line("-- $title --")
        entries.sortedBy { it.key }.forEach { (key, bucket) ->
            line(
                "%-22s %7d/%-7d %s".format(
                    name(key), bucket[0], bucket[1], pct(ratio(bucket[0], bucket[1])),
                ),
            )
        }
    }

    // endregion

    // region judging a false positive

    /** The surface structure that made the checker join two unrelated passwords. */
    private fun cause(first: CorpusEntry, second: CorpusEntry): String = when {
        first.cluster == second.cluster && first.cluster != "none" -> first.cluster
        else -> listOf(first.archetype, second.archetype).sorted().joinToString(" + ")
    }

    /**
     * Whether a flag on an unrelated pair is still worth showing. It is when knowing one
     * password narrows the search for the other: near-identical text, or a shared base a
     * rule-based cracker would try anyway. It is not when both passwords keep material the
     * other tells you nothing about, and never when all they share is a site name, which is
     * public.
     */
    private fun verdict(first: String, second: String): String {
        val canonicalFirst = first.toCharArray().canonicalize()
        val canonicalSecond = second.toCharArray().canonicalize()
        val shorter = min(first.length, second.length)
        val rows = DistanceRows(max(canonicalFirst.size, canonicalSecond.size))
        if (boundedDistance(canonicalFirst, canonicalSecond, shorter, rows) <= 2)
            return "defensible: near-identical text"

        val prefix = commonPrefixLength(canonicalFirst, canonicalSecond)
        val suffix = min(commonSuffixLength(canonicalFirst, canonicalSecond), shorter - prefix)
        val shared = first.take(prefix) + first.takeLast(max(suffix, 0))
        if (SimilarityCorpus.siteNames.any { shared.contains(it, ignoreCase = true) })
            return "wrong: all they share is a site name"

        val restFirst = first.drop(prefix).dropLast(max(suffix, 0))
        val restSecond = second.drop(prefix).dropLast(max(suffix, 0))
        if (isSecret(restFirst) && isSecret(restSecond))
            return "wrong: each keeps material the other does not reveal"

        return "defensible: shared guessable base"
    }

    private val YEAR = Regex("(19|20)\\d{2}")

    /** Whether a fragment is unguessable: long enough, not a word, not just a year. */
    private fun isSecret(fragment: String): Boolean {
        val stripped = YEAR.replace(fragment, "").filter { it.isLetterOrDigit() }
        if (stripped.length < 4) return false
        if (stripped.lowercase() in SimilarityCorpus.vocabulary) return false
        return !stripped.all { it.isDigit() }
    }

    // endregion

    private fun cores() = Runtime.getRuntime().availableProcessors()

    /**
     * Bytes handed out across every live thread. boundedDistance allocates three int rows
     * per comparison that survives its length prefilter, so the sweep churns far more
     * memory than it holds, and that churn does not show up in a heap reading.
     */
    private fun allocatedBytes(): Long {
        val threads = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
            ?: return 0
        return threads.getThreadAllocatedBytes(threads.allThreadIds).filter { it > 0 }.sum()
    }

    private fun gcMillis() =
        ManagementFactory.getGarbageCollectorMXBeans().sumOf { it.collectionTime }

    private fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }

    private fun sizes(name: String, default: List<Int>): List<Int> =
        env(name)?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.isNotEmpty() }
            ?: default

    private fun line(text: String) {
        report.append(text).append('\n')
    }

    private fun ratio(numerator: Number, denominator: Number): Double {
        val d = denominator.toDouble()
        return if (d == 0.0) 0.0 else numerator.toDouble() / d
    }

    private fun pct(value: Double) = "%.2f%%".format(value * 100)
}
