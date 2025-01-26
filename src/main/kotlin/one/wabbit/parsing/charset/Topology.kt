package one.wabbit.parsing.charset

/**
 * Describes the way two [CharSet] instances overlap.
 */
enum class Overlap {
    /** The sets do not intersect at all. */
    EMPTY,
    /** The two sets partially overlap but neither fully contains the other. */
    PARTIAL,
    /** The second set is entirely contained by the first. */
    FIRST_CONTAINS_SECOND,
    /** The first set is entirely contained by the second. */
    SECOND_CONTAINS_FIRST,
    /** The two sets are exactly equal. */
    EQUAL
}

interface SetLike<Set> {
    fun empty(): Set
    fun all(): Set
    fun invert(set: Set): Set
    fun intersect(first: Set, second: Set): Set
    fun union(first: Set, second: Set): Set

    fun isEmpty(set: Set): Boolean = set == empty()
    fun testOverlap(first: Set, second: Set): Overlap {

        if (first === second) return Overlap.EQUAL
        if (first == second) return Overlap.EQUAL

        val intersection = intersect(first, second)
        // if intersection is empty, then first and second are disjoint
        // if intersection is union, then first and second are equal
        // if intersection is first, A /\ B = A, e.g. {1, 2, 3} /\ {1, 2, 3, 4} = {1, 2, 3},
        // then second contains first

        return when {
            isEmpty(intersection) -> Overlap.EMPTY
            intersection == first -> Overlap.SECOND_CONTAINS_FIRST
            intersection == second -> Overlap.FIRST_CONTAINS_SECOND
            else -> Overlap.PARTIAL
        }
    }
}

interface SetLike1<Element, Set> : SetLike<Set> {
    fun lift(element: Element): Set
    fun contains(set: Set, symbol: Element): Boolean
    fun containsAll(set: Set, symbols: Set): Boolean {
        val it = iterator(symbols)
        while (it.hasNext()) {
            if (!contains(set, it.next())) return false
        }
        return true
    }
    fun iterator(set: Set): Iterator<Element>
}

interface Topology<Element, Set, Top> : SetLike1<Element, Set> {
    fun trivial(): Top
    fun refineViaSet(top: Top, set: Set): Top
    fun refine(top: Top, set: Top): Top
    fun basis(set: Top): Iterator<Set>

    companion object {
        val charRanges: Topology<Char, CharSet, CharSetTop> = object : Topology<Char, CharSet, CharSetTop> {
            override fun empty(): CharSet = CharSet.none
            override fun all(): CharSet = CharSet.all

            override fun invert(set: CharSet): CharSet = set.invert()
            override fun intersect(first: CharSet, second: CharSet): CharSet = first.intersect(second)
            override fun union(first: CharSet, second: CharSet): CharSet = first.union(second)

            override fun lift(element: Char): CharSet = CharSet.one(element)
            override fun contains(set: CharSet, symbol: Char): Boolean = set.contains(symbol)
            override fun iterator(set: CharSet): Iterator<Char> = set.iterator()

            override fun trivial(): CharSetTop = CharSetTop.trivial
            override fun refineViaSet(top: CharSetTop, set: CharSet): CharSetTop = top.refine(set)
            override fun refine(top: CharSetTop, set: CharSetTop): CharSetTop = top.refine(set)
            override fun basis(set: CharSetTop): Iterator<CharSet> = set.basis.map { CharSet.of(it) }.iterator()

            override fun isEmpty(set: CharSet): Boolean = set.isEmpty()

            override fun testOverlap(first: CharSet, second: CharSet): Overlap = first.overlap(second)
        }
    }
}
