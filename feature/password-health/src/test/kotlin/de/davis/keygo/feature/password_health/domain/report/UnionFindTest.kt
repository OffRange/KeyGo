package de.davis.keygo.feature.password_health.domain.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UnionFindTest {

    private val uf = UnionFind<Int>()

    @Test
    fun anUnseenElementIsNotContained() {
        assertFalse(uf.contains(1))
    }

    @Test
    fun findingAnElementAddsItAsItsOwnRoot() {
        assertEquals(1, uf.find(1))
        assertTrue(uf.contains(1))
    }

    @Test
    fun unitedElementsShareARoot() {
        uf.union(1, 2)

        assertEquals(uf.find(1), uf.find(2))
        assertTrue(uf.contains(1))
        assertTrue(uf.contains(2))
    }

    @Test
    fun unionIsTransitive() {
        uf.union(1, 2)
        uf.union(3, 4)
        uf.union(2, 3)

        val root = uf.find(1)
        assertEquals(listOf(root, root, root), listOf(2, 3, 4).map(uf::find))
    }

    @Test
    fun separateSetsKeepSeparateRoots() {
        uf.union(1, 2)
        uf.union(3, 4)

        assertNotEquals(uf.find(1), uf.find(3))
    }

    @Test
    fun unitingAnElementWithItselfChangesNothing() {
        uf.union(1, 1)

        assertEquals(1, uf.find(1))
    }

    @Test
    fun unitingTheSameSetTwiceChangesNothing() {
        uf.union(1, 2)
        val root = uf.find(1)

        uf.union(2, 1)

        assertEquals(root, uf.find(1))
        assertEquals(root, uf.find(2))
    }

    @Test
    fun aLongChainStillResolvesToOneRoot() {
        (0 until 1_000).zipWithNext().forEach { (a, b) -> uf.union(a, b) }

        val root = uf.find(0)
        assertTrue((0 until 1_000).all { uf.find(it) == root })
    }
}
