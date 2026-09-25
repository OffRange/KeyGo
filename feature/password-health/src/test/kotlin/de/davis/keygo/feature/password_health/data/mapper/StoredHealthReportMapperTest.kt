package de.davis.keygo.feature.password_health.data.mapper

import com.google.protobuf.kotlin.toByteString
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.feature.password_health.data.local.model.ProtoStoredHealthReport
import de.davis.keygo.feature.password_health.data.local.model.ProtoStoredHealthReportKt
import de.davis.keygo.feature.password_health.data.local.model.protoCheckGap
import de.davis.keygo.feature.password_health.data.local.model.protoRelationFinding
import de.davis.keygo.feature.password_health.data.local.model.protoStoredHealthReport
import de.davis.keygo.feature.password_health.data.local.model.protoStoredItem
import de.davis.keygo.feature.password_health.domain.model.CheckGap
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.GapReason
import de.davis.keygo.feature.password_health.domain.model.HealthFinding
import de.davis.keygo.feature.password_health.domain.model.RelationType
import de.davis.keygo.feature.password_health.domain.model.StoredHealthItem
import de.davis.keygo.feature.password_health.domain.model.StoredHealthReport
import de.davis.keygo.feature.password_health.domain.report.breach
import de.davis.keygo.feature.password_health.domain.report.id
import de.davis.keygo.feature.password_health.domain.report.storedItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class StoredHealthReportMapperTest {

    private val checkedAt = Instant.fromEpochMilliseconds(1_700_000_000_123)

    @Test
    fun aFullReportSurvivesTheRoundTrip() {
        val report = StoredHealthReport(
            items = listOf(
                storedItem(id(0), score = PasswordScore.Weak, breach = breach(12, checkedAt)),
                storedItem(id(1), score = PasswordScore.Ridiculous),
                storedItem(id(2), breach = breach(0, checkedAt)),
                storedItem(id(3), unreadable = true),
            ),
            relationalFindings = listOf(
                HealthFinding.Relation(setOf(id(0), id(1)), RelationType.Reused),
                HealthFinding.Relation(setOf(id(1), id(2), id(3)), RelationType.Similar),
            ),
            gaps = mapOf(
                CheckKind.Breach to CheckGap(GapReason.Unreachable, setOf(id(3))),
                CheckKind.Similarity to CheckGap(GapReason.Failed, setOf(id(0), id(2))),
            ),
            breachCheckEnabled = true,
        )

        assertEquals(report, report.roundTrip())
    }

    @Test
    fun anEmptyReportSurvivesTheRoundTrip() {
        val report = StoredHealthReport(
            items = emptyList(),
            relationalFindings = emptyList(),
            breachCheckEnabled = false,
        )

        assertEquals(report, report.roundTrip())
    }

    @Test
    fun everyScoreSurvivesTheRoundTrip() {
        val scores = listOf(
            PasswordScore.Ridiculous,
            PasswordScore.Weak,
            PasswordScore.Moderate,
            PasswordScore.Strong,
            PasswordScore.Excellent,
        )
        val report = reportOf(scores.mapIndexed { i, score -> storedItem(id(i), score = score) })

        assertEquals(scores, report.roundTrip().items.map { it.score })
    }

    @Test
    fun aNoneScoreIsReadBackAsNoScore() {
        val report = reportOf(listOf(storedItem(id(0), score = PasswordScore.None)))

        assertNull(report.roundTrip().items.single().score)
    }

    @Test
    fun everyGapKindAndReasonSurvivesTheRoundTrip() {
        val kinds = listOf(CheckKind.Strength, CheckKind.Reuse, CheckKind.Breach, CheckKind.Similarity)
        val reasons = listOf(GapReason.Disabled, GapReason.Unreachable, GapReason.Failed)

        reasons.forEach { reason ->
            val report = reportOf(
                items = listOf(storedItem(id(0))),
                gaps = kinds.associateWith { CheckGap(reason, setOf(id(0))) },
            )

            assertEquals(report.gaps, report.roundTrip().gaps)
        }
    }

    @Test
    fun aCleanBreachLookupIsToldApartFromNoLookup() {
        val report = reportOf(
            listOf(
                storedItem(id(0), breach = breach(0, checkedAt)),
                storedItem(id(1), breach = null),
            ),
        )

        assertEquals(listOf(breach(0, checkedAt), null), report.roundTrip().items.map { it.breach })
    }

    @Test
    fun theLookupTimeKeepsMillisecondPrecision() {
        val precise = Instant.fromEpochSeconds(1_700_000_000, 123_456_789)
        val report = reportOf(listOf(storedItem(id(0), breach = breach(1, precise))))

        assertEquals(
            Instant.fromEpochMilliseconds(precise.toEpochMilliseconds()),
            report.roundTrip().items.single().breach?.checkedAt,
        )
    }

    @Test
    fun aRandomIdSurvivesTheRoundTrip() {
        val random = UUID.randomUUID()
        val report = reportOf(listOf(storedItem(random)))

        assertEquals(random, report.roundTrip().items.single().id)
    }

    @Test
    fun theStoredFormIsGzipped() {
        val bytes = reportOf(listOf(storedItem(id(0)))).toCompressedByteArray()

        assertEquals(listOf(0x1f.toByte(), 0x8b.toByte()), bytes.take(2))
    }

    @Test
    fun theStoredFormCarriesTheAlgorithmVersion() {
        val bytes = reportOf(listOf(storedItem(id(0)))).toCompressedByteArray()

        assertEquals(StoredHealthReport.ALGORITHM_VERSION, bytes.unzipProto().algorithmVersion)
    }

    @Test
    fun aReportFromAnotherAlgorithmVersionIsDiscarded() {
        val bytes = protoStoredHealthReport {
            algorithmVersion = StoredHealthReport.ALGORITHM_VERSION + 1
        }.gzip()

        assertNull(bytes.toStoredHealthReport())
    }

    @Test
    fun aReportWithoutAnyVersionIsDiscarded() {
        assertNull(protoStoredHealthReport { }.gzip().toStoredHealthReport())
    }

    @Test
    fun aGapOfAnUnknownKindIsSkipped() {
        val bytes = versioned {
            items += item(0)
            gaps += protoCheckGap {
                kindValue = 99
                uncheckedItemRefs += 0
            }
        }

        assertEquals(emptyMap(), assertNotNullReport(bytes).gaps)
    }

    @Test
    fun aGapForAnUnknownReasonIsSkipped() {
        val bytes = versioned {
            items += item(0)
            gaps += protoCheckGap {
                reasonValue = 99
                uncheckedItemRefs += 0
            }
        }

        assertEquals(emptyMap(), assertNotNullReport(bytes).gaps)
    }

    @Test
    fun aRelationOfAnUnknownTypeIsSkipped() {
        val bytes = versioned {
            items += item(0)
            items += item(1)
            relationFinding += protoRelationFinding {
                itemRefs += listOf(0, 1)
                typeValue = 99
            }
        }

        assertEquals(emptyList(), assertNotNullReport(bytes).relationalFindings)
    }

    @Test
    fun anUnknownScoreIsReadAsNoScore() {
        val bytes = versioned {
            items += protoStoredItem {
                id = ByteArray(16).toByteString()
                fingerprint = byteArrayOf(1).toByteString()
                scoreValue = 99
            }
        }

        assertNull(assertNotNullReport(bytes).items.single().score)
    }

    @Test
    fun aRelationPointingPastTheItemsIsRejected() {
        val bytes = versioned {
            items += item(0)
            relationFinding += protoRelationFinding { itemRefs += listOf(0, 5) }
        }

        assertFailsWith<IllegalStateException> { bytes.toStoredHealthReport() }
    }

    @Test
    fun aGapPointingPastTheItemsIsRejected() {
        val bytes = versioned {
            items += item(0)
            gaps += protoCheckGap { uncheckedItemRefs += 1 }
        }

        assertFailsWith<IllegalStateException> { bytes.toStoredHealthReport() }
    }

    @Test
    fun twoGapsForTheSameKindAreRejected() {
        val bytes = versioned {
            items += item(0)
            gaps += protoCheckGap { uncheckedItemRefs += 0 }
            gaps += protoCheckGap { uncheckedItemRefs += 0 }
        }

        assertFailsWith<IllegalStateException> { bytes.toStoredHealthReport() }
    }

    @Test
    fun anItemListedTwiceCannotBeStored() {
        val report = reportOf(listOf(storedItem(id(0)), storedItem(id(0))))

        assertFailsWith<IllegalStateException> { report.toCompressedByteArray() }
    }

    @Test
    fun aRelationAboutAnItemNotInTheReportCannotBeStored() {
        val report = reportOf(
            items = listOf(storedItem(id(0))),
            relations = listOf(HealthFinding.Relation(setOf(id(0), id(1)), RelationType.Reused)),
        )

        assertFailsWith<IllegalStateException> { report.toCompressedByteArray() }
    }

    @Test
    fun aGapAboutAnItemNotInTheReportCannotBeStored() {
        val report = reportOf(
            items = listOf(storedItem(id(0))),
            gaps = mapOf(CheckKind.Breach to CheckGap(GapReason.Failed, setOf(id(1)))),
        )

        assertFailsWith<IllegalStateException> { report.toCompressedByteArray() }
    }

    @Test
    fun bytesThatAreNotGzipAreRejected() {
        assertFailsWith<IOException> { byteArrayOf(1, 2, 3).toStoredHealthReport() }
    }

    private fun StoredHealthReport.roundTrip() =
        assertNotNullReport(toCompressedByteArray())

    private fun assertNotNullReport(bytes: ByteArray) =
        assertNotNull(bytes.toStoredHealthReport())

    private fun reportOf(
        items: List<StoredHealthItem>,
        relations: List<HealthFinding.Relation> = emptyList(),
        gaps: Map<CheckKind, CheckGap> = emptyMap(),
    ) = StoredHealthReport(
        items = items,
        relationalFindings = relations,
        gaps = gaps,
        breachCheckEnabled = true,
    )

    private fun item(n: Int) = protoStoredItem {
        id = ByteArray(15).plus(n.toByte()).toByteString()
        fingerprint = byteArrayOf(n.toByte()).toByteString()
    }

    private fun versioned(
        block: ProtoStoredHealthReportKt.Dsl.() -> Unit,
    ) = protoStoredHealthReport {
        algorithmVersion = StoredHealthReport.ALGORITHM_VERSION
        block()
    }.gzip()

    private fun ProtoStoredHealthReport.gzip(): ByteArray = ByteArrayOutputStream().also { bos ->
        GZIPOutputStream(bos).use { it.write(toByteArray()) }
    }.toByteArray()

    private fun ByteArray.unzipProto() = ProtoStoredHealthReport.parseFrom(
        GZIPInputStream(ByteArrayInputStream(this)).use { it.readBytes() },
    )
}
