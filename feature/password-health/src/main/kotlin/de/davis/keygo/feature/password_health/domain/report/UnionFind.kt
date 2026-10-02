package de.davis.keygo.feature.password_health.domain.report

internal class UnionFind<T> {
    private val parent = HashMap<T, T>()
    fun contains(x: T) = x in parent

    fun find(x: T): T {
        val p = parent.getOrPut(x) { x }
        return if (p == x) x else find(p).also { parent[x] = it }
    }

    fun union(a: T, b: T) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[ra] = rb
    }
}
