// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.parsing.charset

/** Describes the way two [CharSet] instances overlap. */
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
    EQUAL,
}

/**
 * Minimal algebra required for a concrete set representation.
 *
 * @param Set concrete set representation.
 */
interface SetLike<Set> {
    /** Return the empty set value. */
    fun empty(): Set

    /** Return the universal set value. */
    fun all(): Set

    /**
     * Return the complement of [set] relative to [all].
     *
     * @param set set to complement.
     * @return set containing every element not present in [set].
     */
    fun invert(set: Set): Set

    /**
     * Return the intersection of [first] and [second].
     *
     * @param first left operand.
     * @param second right operand.
     * @return set containing elements present in both operands.
     */
    fun intersect(first: Set, second: Set): Set

    /**
     * Return the union of [first] and [second].
     *
     * @param first left operand.
     * @param second right operand.
     * @return set containing elements present in either operand.
     */
    fun union(first: Set, second: Set): Set

    /**
     * Return whether [set] is equal to [empty].
     *
     * Implementations may override this when emptiness can be tested more cheaply than equality.
     *
     * @param set set to test.
     * @return `true` when [set] is empty.
     */
    fun isEmpty(set: Set): Boolean = set == empty()

    /**
     * Classify how [first] overlaps [second].
     *
     * The default implementation computes an intersection and compares it with each operand.
     * Implementations may override this with a representation-specific implementation.
     *
     * @param first left operand.
     * @param second right operand.
     * @return overlap relationship between the operands.
     */
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

/**
 * Set algebra for sets whose individual elements can be lifted, tested, and iterated.
 *
 * @param Element element type represented by the set.
 * @param Set concrete set representation.
 */
interface SetLike1<Element, Set> : SetLike<Set> {
    /**
     * Return the singleton set containing [element].
     *
     * @param element element to lift.
     * @return singleton set.
     */
    fun lift(element: Element): Set

    /**
     * Return whether [set] contains [symbol].
     *
     * @param set set to test.
     * @param symbol element to look up.
     * @return `true` when [symbol] is in [set].
     */
    fun contains(set: Set, symbol: Element): Boolean

    /**
     * Return whether [set] contains every element represented by [symbols].
     *
     * @param set candidate superset.
     * @param symbols set whose elements must all be present in [set].
     * @return `true` when every element from [symbols] is present in [set].
     */
    fun containsAll(set: Set, symbols: Set): Boolean {
        val it = iterator(symbols)
        while (it.hasNext()) {
            if (!contains(set, it.next())) return false
        }
        return true
    }

    /**
     * Iterate over the elements represented by [set].
     *
     * @param set set to iterate.
     * @return iterator over elements in implementation-defined order.
     */
    fun iterator(set: Set): Iterator<Element>
}

/**
 * Set algebra plus a finite partition of the whole element space.
 *
 * A topology can refine partitions as sets are introduced, then expose partition basis sets for
 * downstream parser or automaton construction.
 *
 * @param Element element type represented by the set.
 * @param Set concrete set representation.
 * @param Top concrete top-level partition representation.
 */
interface Topology<Element, Set, Top> : SetLike1<Element, Set> {
    /** Return the coarsest partition of the whole element space. */
    fun trivial(): Top

    /**
     * Refine [top] so it also respects the boundaries of [set].
     *
     * @param top existing partition.
     * @param set set whose boundaries should become partition boundaries.
     * @return refined partition.
     */
    fun refineViaSet(top: Top, set: Set): Top

    /**
     * Refine [top] so it also respects every boundary present in [set].
     *
     * @param top existing partition.
     * @param set second partition whose boundaries should be included.
     * @return refined partition.
     */
    fun refine(top: Top, set: Top): Top

    /**
     * Iterate over the set basis represented by [set].
     *
     * @param set partition to decompose.
     * @return iterator over basis sets.
     */
    fun basis(set: Top): Iterator<Set>

    companion object {
        /** Character topology backed by [CharSet] and [CharSetTop]. */
        val charRanges: Topology<Char, CharSet, CharSetTop> =
            object : Topology<Char, CharSet, CharSetTop> {
                override fun empty(): CharSet = CharSet.none

                override fun all(): CharSet = CharSet.all

                override fun invert(set: CharSet): CharSet = set.invert()

                override fun intersect(first: CharSet, second: CharSet): CharSet =
                    first.intersect(second)

                override fun union(first: CharSet, second: CharSet): CharSet = first.union(second)

                override fun lift(element: Char): CharSet = CharSet.one(element)

                override fun contains(set: CharSet, symbol: Char): Boolean = set.contains(symbol)

                override fun iterator(set: CharSet): Iterator<Char> = set.iterator()

                override fun trivial(): CharSetTop = CharSetTop.trivial

                override fun refineViaSet(top: CharSetTop, set: CharSet): CharSetTop =
                    top.refine(set)

                override fun refine(top: CharSetTop, set: CharSetTop): CharSetTop = top.refine(set)

                override fun basis(set: CharSetTop): Iterator<CharSet> =
                    set.basis.map { CharSet.of(it) }.iterator()

                override fun isEmpty(set: CharSet): Boolean = set.isEmpty()

                override fun testOverlap(first: CharSet, second: CharSet): Overlap =
                    first.overlap(second)
            }
    }
}
