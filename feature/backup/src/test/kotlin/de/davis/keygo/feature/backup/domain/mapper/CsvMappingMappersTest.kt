package de.davis.keygo.feature.backup.domain.mapper

import de.davis.keygo.feature.backup.domain.model.CsvColumnType
import de.davis.keygo.feature.backup.domain.model.MappingConfidence
import de.davisalessandro.keygo.rust.ColumnMapping
import de.davisalessandro.keygo.rust.Confidence
import de.davisalessandro.keygo.rust.CsvAnalysis
import de.davisalessandro.keygo.rust.CsvColumn
import de.davisalessandro.keygo.rust.FieldConfidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CsvMappingMappersTest {

    @Test
    fun `toDomain folds suggested type and confidence onto each column`() {
        val analysis = CsvAnalysis(
            columns = listOf(
                CsvColumn(index = 0u, header = "name", sampleValues = listOf("Email", "Bank")),
                CsvColumn(index = 1u, header = "field_a", sampleValues = listOf("alice@ex.com")),
                CsvColumn(index = 2u, header = "favorite", sampleValues = listOf("1", "0")),
            ),
            suggested = ColumnMapping(
                title = 0u, url = null, username = 1u, password = null, notes = null, totp = null,
            ),
            confidence = FieldConfidence(
                title = Confidence.HIGH,
                url = null,
                username = Confidence.MEDIUM,
                password = null,
                notes = null,
                totp = null,
            ),
        )

        val domain = analysis.toDomain()

        assertEquals(3, domain.columns.size)
        assertEquals(CsvColumnType.Title, domain.columns[0].suggestedType)
        assertEquals(MappingConfidence.High, domain.columns[0].confidence)
        assertEquals(listOf("Email", "Bank"), domain.columns[0].samples)
        assertEquals(CsvColumnType.Username, domain.columns[1].suggestedType)
        assertEquals(MappingConfidence.Medium, domain.columns[1].confidence)
        assertNull(domain.columns[2].suggestedType) // unmatched column
        assertNull(domain.columns[2].confidence)
    }

    @Test
    fun `toColumnMapping places each assigned type at its column index`() {
        val assignment = mapOf(
            0 to CsvColumnType.Title,
            1 to null, // Ignore
            2 to CsvColumnType.Password,
            3 to CsvColumnType.Totp,
        )

        val mapping = assignment.toColumnMapping()

        assertEquals(0u, mapping.title)
        assertEquals(2u, mapping.password)
        assertEquals(3u, mapping.totp)
        assertNull(mapping.url)
        assertNull(mapping.username)
        assertNull(mapping.notes)
    }
}
