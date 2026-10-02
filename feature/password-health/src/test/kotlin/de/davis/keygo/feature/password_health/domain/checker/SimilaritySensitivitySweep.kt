package de.davis.keygo.feature.password_health.domain.checker

import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

/**
 * Two follow-ups to [SimilarPasswordCheckerBenchmark]: which of the checker's three rules
 * produces the false pairs, and whether moving its constants can reach an operating point
 * worth shipping.
 *
 *     SIMILARITY_VAULTS=2000 ./gradlew :feature:password-health:testDebugUnitTest \
 *       --tests '*SimilaritySensitivitySweep*'
 *
 * The report lands in `build/reports/similarity/sweep.txt`.
 */
class SimilaritySensitivitySweep {

    private val report = StringBuilder()

    /** The replica is only worth reading if it still agrees with the shipped checker. */
    @Test
    fun replicaMatchesShippedChecker() = runTest(timeout = 1.hours) {
        SimilarityCorpus.vaults(60, seed = 4242L).forEach { corpus ->
            val candidates = corpus.candidates()
            val shipped = HashSet<PairKey>()
            SimilarPasswordChecker().check(candidates)
                .findings
                .filterIsInstance<HealthFinding.Relation>()
                .forEach { finding ->
                    val ids = finding.relatedItemIds.toList()
                    for (i in ids.indices) for (j in i + 1..ids.lastIndex)
                        shipped += PairKey.of(ids[i], ids[j])
                }
            assertEquals(shipped, TunableSimilarity().edges(candidates).keys)
        }
    }

    @Test
    fun sweep() = runTest(timeout = 4.hours) {
        val count = System.getenv("SIMILARITY_VAULTS")?.toIntOrNull() ?: 500
        val corpora = SimilarityCorpus.vaults(count)
        val passwords = corpora.sumOf { it.entries.size }

        line("SimilarPasswordChecker sensitivity sweep")
        line("$count simulated vaults, $passwords passwords")
        line("")

        attribution(corpora)

        line("== how much the result depends on the vocabulary assumption ==")
        header()
        listOf(
            1.0 to "a dozen favourite words",
            2.0 to "twice as many",
            4.0 to "rarely repeats one"
        )
            .forEach { (scale, label) ->
                score(
                    "personal vocabulary x$scale ($label)",
                    TunableSimilarity(),
                    SimilarityCorpus.vaults(count, vocabularyScale = scale),
                )
            }
        line("")

        line("== moving the constants ==")
        header()
        settings().forEach { (label, similarity) -> score(label, similarity, corpora) }
        line("")

        line("== finer grid ==")
        header()
        grid().forEach { (label, similarity) -> score(label, similarity, corpora) }
        line("")

        // Exactly what the edit cap costs: the same checker with and without it.
        detail("no edit cap", TunableSimilarity(maxDistance = Int.MAX_VALUE), corpora)
        detail("current, cap 4", TunableSimilarity(), corpora)

        val out = File(
            System.getenv("SIMILARITY_REPORT_DIR") ?: "build/reports/similarity",
            "sweep.txt",
        )
        out.parentFile?.mkdirs()
        out.writeText(report.toString())
        println(report)
        println("report written to ${out.absolutePath}")
    }

    /** Which rule is responsible for the right answers and which for the wrong ones. */
    private fun attribution(corpora: List<Corpus>) {
        val truePositives = HashMap<TunableSimilarity.Rule, Long>()
        val falsePositives = HashMap<TunableSimilarity.Rule, Long>()
        val examples = HashMap<TunableSimilarity.Rule, MutableList<String>>()

        corpora.forEach { corpus ->
            TunableSimilarity().edges(corpus.candidates()).forEach { (pair, rule) ->
                when {
                    pair in corpus.variantPairs ->
                        truePositives[rule] = (truePositives[rule] ?: 0) + 1

                    pair in corpus.duplicatePairs -> Unit

                    else -> {
                        falsePositives[rule] = (falsePositives[rule] ?: 0) + 1
                        val bucket = examples.getOrPut(rule) { mutableListOf() }
                        if (bucket.size < 6) bucket += "%-30s  %-30s".format(
                            corpus.byId.getValue(pair.a).password,
                            corpus.byId.getValue(pair.b).password,
                        )
                    }
                }
            }
        }

        line("== which rule fired ==")
        line("%-14s %12s %12s %12s".format("rule", "TP", "FP", "precision"))
        TunableSimilarity.Rule.entries.forEach { rule ->
            val tp = truePositives[rule] ?: 0
            val fp = falsePositives[rule] ?: 0
            line("%-14s %12d %12d %12s".format(rule.name, tp, fp, pct(ratio(tp, tp + fp))))
        }
        line("")
        TunableSimilarity.Rule.entries.forEach { rule ->
            line("-- false pairs from ${rule.name} --")
            examples[rule].orEmpty().forEach { line("   $it") }
        }
        line("")
    }

    /** Where the two live thresholds sit on their curves, so each value is a decision. */
    private fun grid(): List<Pair<String, TunableSimilarity>> =
        listOf(0.25, 0.3, 0.35, 0.4, 0.45, 0.5, 0.6, 0.7).map { share ->
            "affixShare $share" to TunableSimilarity(affixShare = share)
        } + listOf(3, 4, 5, 6, 8).map { cap ->
            "edit budget capped at $cap" to TunableSimilarity(maxDistance = cap)
        }

    /** Each part of the fix switched off on its own, to show what it is worth. */
    private fun settings(): List<Pair<String, TunableSimilarity>> = listOf(
        "current" to TunableSimilarity(),
        "edit rule asks for no evidence" to TunableSimilarity(requireEvidence = false),
        "evidence needs 2 shared words" to TunableSimilarity(minSharedWords = 2),
        "containment rule off" to TunableSimilarity(minContainedLength = 0),
        "containment needs 12 characters" to TunableSimilarity(minContainedLength = 12),
        "a number may move any distance" to TunableSimilarity(maxCounterStep = Long.MAX_VALUE),
        "a number may move by 1" to TunableSimilarity(maxCounterStep = 1),
        "a number may move by 10" to TunableSimilarity(maxCounterStep = 10),
        "shared words buy half the budget" to TunableSimilarity(weakEvidenceDivisor = 2),
        "shared words buy nothing" to TunableSimilarity(sharedWordsAsEvidence = Int.MAX_VALUE),
        "word affixes count again" to TunableSimilarity(ignoreWordAffixes = false),
        "measured against raw length" to
                TunableSimilarity(denominator = TunableSimilarity.Denominator.Longer),
        "measured against the shorter password" to
                TunableSimilarity(denominator = TunableSimilarity.Denominator.Shorter),
        "both reverted, old thresholds" to TunableSimilarity(
            ignoreWordAffixes = false,
            denominator = TunableSimilarity.Denominator.Shorter,
            affixShare = 0.3,
            wordAffixShare = 0.55,
        ),
        "current + minTemplateLength 10" to TunableSimilarity(minTemplateLength = 10),
        "current + minAffixLength 5" to TunableSimilarity(minAffixLength = 5),
        "current + maxDistanceRatio 6" to TunableSimilarity(maxDistanceRatio = 6),
    )

    private fun score(label: String, similarity: TunableSimilarity, corpora: List<Corpus>) {
        var truePositives = 0L
        var falsePositives = 0L
        var truePairs = 0L
        corpora.forEach { corpus ->
            truePairs += corpus.variantPairs.size
            similarity.edges(corpus.candidates()).keys.forEach { pair ->
                when {
                    pair in corpus.variantPairs -> truePositives++
                    pair in corpus.duplicatePairs -> Unit
                    else -> falsePositives++
                }
            }
        }
        val precision = ratio(truePositives, truePositives + falsePositives)
        val recall = ratio(truePositives, truePairs)
        val f1 = if (precision + recall == 0.0) 0.0
        else 2 * precision * recall / (precision + recall)
        line(
            "%-46s %10s %10s %8.4f %12d".format(
                label, pct(precision), pct(recall), f1, falsePositives,
            ),
        )
    }

    /**
     * Recall variation by variation and by length, so a precision gain can be paid for
     * knowingly. The length bucket is what shows where a rule that only bites on long
     * passwords actually costs anything.
     */
    private fun detail(label: String, similarity: TunableSimilarity, corpora: List<Corpus>) {
        val byVariation = HashMap<String, LongArray>()
        val byLength = HashMap<String, LongArray>()
        val byShape = HashMap<String, LongArray>()
        corpora.forEach { corpus ->
            val flagged = similarity.edges(corpus.candidates()).keys
            corpus.variantPairs.forEach { pair ->
                val first = corpus.byId.getValue(pair.a)
                val second = corpus.byId.getValue(pair.b)
                val kind = when {
                    first.relation == "base" -> second.relation
                    second.relation == "base" -> first.relation
                    else -> "variant-of-variant"
                }
                val shorter = minOf(first.password.length, second.password.length)
                val length = when {
                    shorter < 12 -> "under 12"
                    shorter < 16 -> "12 to 15"
                    shorter < 20 -> "16 to 19"
                    shorter < 28 -> "20 to 27  (cap bites)"
                    else -> "28 and over  (cap bites)"
                }
                val hit = pair in flagged
                byVariation.tally(kind, hit)
                byLength.tally(length, hit)
                byShape.tally(first.archetype, hit)
            }
        }
        listOf(
            "variation" to byVariation,
            "length of the shorter password" to byLength,
            "shape" to byShape,
        ).forEach { (name, buckets) ->
            line("-- recall by $name, $label --")
            buckets.entries.sortedBy { it.key }.forEach { (kind, bucket) ->
                line(
                    "%-26s %7d/%-7d %s".format(
                        kind, bucket[0], bucket[1], pct(ratio(bucket[0], bucket[1])),
                    ),
                )
            }
        }
        line("")
    }

    private fun HashMap<String, LongArray>.tally(key: String, hit: Boolean) {
        val bucket = getOrPut(key) { LongArray(2) }
        bucket[1]++
        if (hit) bucket[0]++
    }

    private fun header() = line(
        "%-46s %10s %10s %8s %12s".format("setting", "precision", "recall", "F1", "false pairs"),
    )

    private fun line(text: String) {
        report.append(text).append('\n')
    }

    private fun ratio(numerator: Number, denominator: Number): Double {
        val d = denominator.toDouble()
        return if (d == 0.0) 0.0 else numerator.toDouble() / d
    }

    private fun pct(value: Double) = "%.2f%%".format(value * 100)
}
