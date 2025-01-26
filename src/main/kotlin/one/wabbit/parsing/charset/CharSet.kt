package one.wabbit.parsing.charset

import one.wabbit.formatting.escapeJavaChar
import java.util.*
import kotlin.collections.ArrayList

/**
 * Escapes a single character in a Java-compatible way.
 *
 * This function is intended to be an inline extension that
 * transforms a given [Char] into a string with escape sequences
 * recognized by Java (e.g. `\n`, `\t`, etc.).
 */
private inline fun Char.formatChar() = escapeJavaChar(this)

/**
 * Represents a set of `Char` values, stored as sorted, non-overlapping, and non-adjacent ranges.
 *
 * Internally, each pair of indices `[0,1], [2,3], ...` in the backing array
 * corresponds to a single continuous range of characters `[start, end]`.
 *
 * The [CharSet] class provides a variety of set-theoretic operations (union, intersection,
 * difference), membership checks, iteration over contained characters, and transformations.
 *
 * Instances are immutable after construction. The companion object provides convenient
 * factory methods for creating [CharSet] instances from single characters, ranges,
 * or arbitrary collections of characters.
 *
 * @constructor
 * Private constructor used by factory methods and internal builders.
 *
 * @property set The internal `CharArray` storing `[start, end]` pairs for each disjoint range.
 */
class CharSet private constructor(private val set: CharArray) {
    init {
        // Validate the internal range representation.
        assertValidRangeList(set)
    }

    /**
     * A cached value combining both a hash code and the size in a single 64-bit value.
     *
     * This is used to lazily compute the set’s [hashCode] and size
     * for performance reasons. The special value `0x00000000FFFFFFFFUL`
     * indicates uninitialized.
     */
    private var hashCodeAndSize: ULong = 0x00000000FFFFFFFFUL

    /**
     * Ensures the [hashCodeAndSize] cache is computed. Returns the combined value,
     * where the top 32 bits are the `contentHashCode()` and the lower 32 bits
     * are the size of the set.
     */
    private fun ensureHashCodeAndSizeAreComputed(): ULong {
        var localHashCodeAndSize = hashCodeAndSize
        if (localHashCodeAndSize == 0x00000000FFFFFFFFUL) {
            var size = 0
            val rangeCount = set.size / 2
            for (i in 0 until rangeCount) {
                val start = set[2 * i]
                val end = set[2 * i + 1]
                size += end - start + 1
            }
            localHashCodeAndSize = (set.contentHashCode().toULong() shl 32) or (size.toULong() and 0xFFFFFFFFUL)
            hashCodeAndSize = localHashCodeAndSize
        }
        return localHashCodeAndSize
    }

    /**
     * The number of disjoint ranges stored in this set.
     *
     * Each pair of `[start, end]` in the backing [CharArray] is
     * considered a single range, so this value is `set.size / 2`.
     */
    val nonConsecutiveRangeCount: Int = set.size / 2

    /**
     * The total number of characters contained in this set.
     *
     * If the set has multiple disjoint ranges, the size is the sum of
     * all `end - start + 1` for each range.
     */
    val size: Int get() = (ensureHashCodeAndSizeAreComputed() and 0xFFFFFFFFUL).toInt()

    /**
     * Returns a hash code for this set, derived from the backing array.
     *
     * The hash code is stable for the lifetime of the instance, and is
     * cached internally.
     */
    override fun hashCode(): Int = (ensureHashCodeAndSizeAreComputed() shr 32).toInt()

    /**
     * Determines equality by comparing the backing arrays of both [CharSet] instances.
     *
     * Two [CharSet]s are considered equal if they represent the exact same ranges
     * in the same order.
     *
     * @return `true` if both sets have identical ranges; `false` otherwise.
     */
    override fun equals(other: Any?): Boolean {
        if (other !is CharSet) return false
        return this.set.contentEquals(other.set)
    }

    /**
     * @return `true` if this set contains no characters.
     */
    fun isEmpty(): Boolean = set.isEmpty()

    /**
     * @return `true` if this set contains at least one character.
     */
    fun isNotEmpty(): Boolean = set.isNotEmpty()

    /**
     * @return `true` if this set represents all possible `Char` values
     * (`[Char.MIN_VALUE .. Char.MAX_VALUE]`).
     */
    fun isAll(): Boolean =
        set.size == 2 && set[0] == Char.MIN_VALUE && set[1] == Char.MAX_VALUE

    /**
     * Returns the character at a given [index] if you laid out all characters in this
     * set contiguously.
     *
     * For example, if the set is `[a-dx-z]`, then `get(0) == 'a'`, `get(1) == 'b'`,
     * up through `get(3) == 'd'`, etc.
     *
     * @throws IndexOutOfBoundsException if the [index] exceeds the size of this set.
     */
    operator fun get(index: Int): Char {
        var i = index
        val rangeCount = set.size / 2
        for (rangeIndex in 0 until rangeCount) {
            val first = set[2 * rangeIndex]
            val last = set[2 * rangeIndex + 1]
            val size = last - first + 1
            if (i < size) return first + i
            i -= size
        }
        throw IndexOutOfBoundsException()
    }

    /**
     * Returns a pseudo-randomly selected character from this set using a [SplittableRandom].
     *
     * @param random The random number generator to use.
     * @return A randomly sampled character within this set’s range.
     * @throws NoSuchElementException if the set is empty.
     */
    fun sample(random: SplittableRandom): Char = this[random.nextInt(size)]

    /**
     * Returns a pseudo-randomly selected character from this set using a standard [Random].
     *
     * @param random The random number generator to use.
     * @return A randomly sampled character within this set’s range.
     * @throws NoSuchElementException if the set is empty.
     */
    fun sample(random: Random): Char = this[random.nextInt(size)]

    /**
     * Checks whether this set contains a given character [c].
     *
     * Uses a linear search for small sets (≤ 16 intervals),
     * and a binary search for larger sets.
     *
     * @param c The character to check for membership.
     * @return `true` if [c] is in this set; `false` otherwise.
     */
    operator fun contains(c: Char): Boolean {
        // Use linear search if we have ≤ 16 intervals, otherwise binary search.
        if (set.size <= 32) {
            val rangeCount = set.size / 2
            for (rangeIndex in 0 until rangeCount) {
                val first = set[2 * rangeIndex]
                val last = set[2 * rangeIndex + 1]
                if (c in first..last) return true
            }
            return false
        } else {
            val i = set.binarySearch(c)

            // If i >= 0, then some range has c as its first or last element.
            if (i >= 0) return true

            // Otherwise, j is the insertion point.
            val j = -i - 1
            // If j is even, c is before the first element of some range.
            // If j is odd, so c is after the first element of some range and
            // before the last element of that range.
            return j % 2 != 0
        }
    }

    /**
     * Checks whether this set contains all characters in a specified collection [cs].
     *
     * @param cs A collection of characters to test.
     * @return `true` if every character in [cs] is contained in this set.
     */
    fun containsAll(cs: Collection<Char>): Boolean {
        for (c in cs) {
            if (c !in this) return false
        }
        return true
    }

    /**
     * Checks whether this set contains every character from another [CharSet] [that].
     *
     * @param that Another [CharSet].
     * @return `true` if this set is a superset of [that].
     */
    fun containsAll(that: CharSet): Boolean {
        val thisRangeCount = this.set.size / 2
        val thatRangeCount = that.set.size / 2

        var i = 0
        var j = 0

        if (this.set.isEmpty() && that.set.isNotEmpty()) return false

        while (i < thisRangeCount && j < thatRangeCount) {
//            val a = this.set[i]
//            val b = that.set[j]
            val aFirst = this.set[2 * i]
            val aLast = this.set[2 * i + 1]
            val bFirst = that.set[2 * j]
            val bLast = that.set[2 * j + 1]
            if (aLast < bFirst) {
                i++
            } else if (aFirst <= bFirst && bLast <= aLast) {
                j++
            } else {
                return false
            }
        }

        if (j < thatRangeCount) return false
        return true
    }

    /**
     * Returns an [Overlap] enum indicating how this set and [that] set overlap.
     * Possible outcomes: [Overlap.EMPTY], [Overlap.FIRST_CONTAINS_SECOND],
     * [Overlap.SECOND_CONTAINS_FIRST], [Overlap.EQUAL], or [Overlap.PARTIAL].
     *
     * @param that Another [CharSet].
     * @return An [Overlap] value describing the relationship.
     */
    fun overlap(that: CharSet): Overlap {
        /* when {
            A /\ B == none   -> Overlap.EMPTY
            A /\ B == A      -> Overlap.SECOND_CONTAINS_FIRST
            A /\ B == B      -> Overlap.FIRST_CONTAINS_SECOND
            A /\ B == A \/ B -> Overlap.EQUAL
            else             -> Overlap.PARTIAL
        } */
        val thisRangeCount = this.set.size / 2
        val thatRangeCount = that.set.size / 2

        var total = 0        // intersected intervals
        var totalA = 0       // intersected intervals where A /\ B == A
        var totalB = 0       // intersected intervals where A /\ B == B
        var totalEqual = 0   // intersected intervals where A /\ B == A \/ B
        var extraA = 0       // extra intervals in A
        var extraB = 0       // extra intervals in B

        var aIndex = 0
        var bIndex = 0
        while (aIndex < thisRangeCount && bIndex < thatRangeCount) {
            val aFirst = this.set[2 * aIndex]
            val aLast = this.set[2 * aIndex + 1]
            val bFirst = that.set[2 * bIndex]
            val bLast = that.set[2 * bIndex + 1]

            if (aLast < bFirst) {
                // IF IT WAS UNION:
                // builder.addPossiblyOverlapping(aFirst, aLast)
                // (A has some extra stuff)
                // println("[${aFirst.formatChar()}-${aLast.formatChar()}] (A)")
                extraA += 1
                aIndex++
            } else if (bLast < aFirst) {
                // IF IT WAS UNION:
                // builder.addPossiblyOverlapping(bFirst, bLast)
                // (B has some extra stuff)
                // println("[${bFirst.formatChar()}-${bLast.formatChar()}] (B)")
                extraB += 1
                bIndex++
            } else {
                val first = maxOf(aFirst, bFirst)
                val last = minOf(aLast, bLast)
//                println("[${aFirst.formatChar()}-${aLast.formatChar()}] (A) /\\ " +
//                        "[${bFirst.formatChar()}-${bLast.formatChar()}] (B) = " +
//                        "[${first.formatChar()}-${last.formatChar()}]")

                total += 1
                if (first == aFirst && first == bFirst && last == aLast && last == bLast) {
                    // A == B
                    totalEqual += 1
                } else if (first == aFirst && last == aLast) {
                    // A is contained in B
                    totalA += 1
                } else if (first == bFirst && last == bLast) {
                    // B is contained in A
                    totalB += 1
                } else {
                    // println("partial")
                    return Overlap.PARTIAL
                }

                if (aLast == bLast) {
                    aIndex++
                    bIndex++
                } else if (aLast < bLast) {
                    aIndex++
                } else {
                    bIndex++
                }
            }
        }

        while (aIndex < thisRangeCount) {
            val aFirst = this.set[2 * aIndex]
            val aLast = this.set[2 * aIndex + 1]
            // println("[${aFirst.formatChar()}-${aLast.formatChar()}] (A)")
            extraA += 1
            aIndex++
        }

        while (bIndex < thatRangeCount) {
            val bFirst = that.set[2 * bIndex]
            val bLast = that.set[2 * bIndex + 1]
            // println("[${bFirst.formatChar()}-${bLast.formatChar()}] (B)")
            extraB += 1
            bIndex++
        }

//        println("total = $total")
//        println("totalA = $totalA")
//        println("totalB = $totalB")
//        println("totalEqual = $totalEqual")
//        println("totalPartial = $totalPartial")
//        println("extraA = $extraA")
//        println("extraB = $extraB")

        if (total == 0) return Overlap.EMPTY
        if (total == totalEqual) {
            if (extraA > 0 && extraB > 0) return Overlap.PARTIAL
            if (extraA > 0) return Overlap.FIRST_CONTAINS_SECOND
            if (extraB > 0) return Overlap.SECOND_CONTAINS_FIRST
            return Overlap.EQUAL
        }
        if (total == totalA + totalEqual) {
            if (extraA > 0) return Overlap.PARTIAL
            return Overlap.SECOND_CONTAINS_FIRST
        }
        if (total == totalB + totalEqual) {
            if (extraB > 0) return Overlap.PARTIAL
            return Overlap.FIRST_CONTAINS_SECOND
        }
        return Overlap.PARTIAL
    }

    /**
     * Returns a new [CharSet] by transforming each character in this set with the
     * given [transform] function. The result is then built as a minimal set of
     * disjoint ranges.
     *
     * @param transform A mapping from a `Char` to another `Char`.
     * @return A new [CharSet] containing the transformed characters.
     */
    fun map(transform: (Char) -> Char): CharSet {
        val array = CharArray(size)
        var i = 0
        val rangeCount = set.size / 2
        for (rangeIndex in 0 until rangeCount) {
            val first = set[2 * rangeIndex]
            val last = set[2 * rangeIndex + 1]
            for (c in first..last) {
                array[i++] = transform(c)
            }
        }
        return unsafeFromChars(array)
    }

    /**
     * Returns a new [CharSet] containing only the characters from this set that satisfy
     * the given [predicate].
     *
     * @param predicate A test function that returns `true` if a character should be included.
     * @return A new [CharSet] containing only those characters for which [predicate] is `true`.
     */
    fun filter(predicate: (Char) -> Boolean): CharSet {
        val newSet = mutableListOf<Char>()
        val rangeCount = set.size / 2
        for (rangeIndex in 0 until rangeCount) {
            val first = set[2 * rangeIndex]
            val last = set[2 * rangeIndex + 1]
            var start = -1
            for (i in first..last) {
                if (predicate(i)) {
                    if (start == -1) start = i.code
                } else {
                    if (start != -1) {
                        newSet.add(start.toChar())
                        newSet.add(i.dec())
                        start = -1
                    }
                }
            }
            if (start != -1) {
                newSet.add(start.toChar())
                newSet.add(last)
            }
        }
        return CharSet(newSet.toCharArray())
    }

    /**
     * Counts how many characters in this set satisfy the given [predicate].
     *
     * @param predicate A test function for each character.
     * @return The number of characters for which [predicate] returned `true`.
     */
    fun count(predicate: (Char) -> Boolean): Int {
        var result = 0
        val rangeCount = set.size / 2
        for (rangeIndex in 0 until rangeCount) {
            val first = set[2 * rangeIndex]
            val last = set[2 * rangeIndex + 1]
            for (i in first..last)
                if (predicate(i)) result++
        }
        return result
    }

    /**
     * Creates and returns a new [CharSet] whose contents are the inverse of this set
     * with respect to the entire `[Char.MIN_VALUE .. Char.MAX_VALUE]` range.
     *
     * If this set is empty, the inversion is the universal set of all `Char` values.
     *
     * @return A new [CharSet] that contains all characters not in this set.
     */
    fun invert(): CharSet {
        if (set.isEmpty()) {
            return all
        }

        val builder = Builder()
        val firstFirst = set[0]
        if (firstFirst > Char.MIN_VALUE) {
            builder.addNonAdjacent(Char.MIN_VALUE until firstFirst)
        }

        val rangeCount = set.size / 2
        for (rangeIndex in 0 until rangeCount - 1) {
            val currentLast = set[2 * rangeIndex + 1]
            val nextFirst = set[2 * rangeIndex + 2]
            builder.addNonAdjacent(currentLast + 1 until nextFirst)
        }

        val lastLast = set[set.size - 1]
        if (lastLast < Char.MAX_VALUE) {
            builder.addNonAdjacent(lastLast + 1..Char.MAX_VALUE)
        }

        return builder.build()
    }

    /**
     * Returns a new [CharSet] representing the union of this set and [other].
     *
     * @param other Another [CharSet].
     * @return A new [CharSet] that contains every character in either set.
     */
    infix fun union(other: CharSet): CharSet {
        // Computes the union of two sets.

        val builder = Builder()

        val thisRangeCount = this.set.size / 2
        val thatRangeCount = other.set.size / 2
        var i = 0
        var j = 0

        while (i < thisRangeCount && j < thatRangeCount) {
            val aFirst = this.set[2 * i]
            val aLast = this.set[2 * i + 1]
            val bFirst = other.set[2 * j]
            val bLast = other.set[2 * j + 1]

            if (aLast < bFirst) {
                builder.addPossiblyOverlapping(aFirst, aLast)
                i++
            } else if (bLast < aFirst) {
                builder.addPossiblyOverlapping(bFirst, bLast)
                j++
            } else {
                val first = minOf(aFirst, bFirst)
                val last = maxOf(aLast, bLast)
                builder.addPossiblyOverlapping(first, last)
                i++
                j++
            }
        }

        while (i < thisRangeCount) {
            builder.addPossiblyOverlapping(this.set[2 * i], this.set[2 * i + 1])
            i++
        }

        while (j < thatRangeCount) {
            builder.addPossiblyOverlapping(other.set[2 * j], other.set[2 * j + 1])
            j++
        }

        return builder.build()
    }

    /**
     * Returns a new [CharSet] representing the intersection of this set and [other].
     *
     * @param other Another [CharSet].
     * @return A new [CharSet] that contains every character in both sets.
     */
    infix fun intersect(other: CharSet): CharSet {
        // Computes the intersection of two sets.
        val result = Builder()

        val thisRangeCount = this.set.size / 2
        val thatRangeCount = other.set.size / 2

        var i = 0
        var j = 0
        while (i < thisRangeCount && j < thatRangeCount) {
            val aFirst = this.set[2 * i]
            val aLast = this.set[2 * i + 1]
            val bFirst = other.set[2 * j]
            val bLast = other.set[2 * j + 1]

            if (aLast < bFirst) {
                i++
            } else if (bLast < aFirst) {
                j++
            } else {
                val first = maxOf(aFirst, bFirst)
                val last = minOf(aLast, bLast)
                // addRange(first..last)
                result.addPossiblyOverlapping(first, last)

                if (aLast < bLast) {
                    i++
                } else {
                    j++
                }
            }
        }

        return result.build()
    }

    /**
     * Returns a new [CharSet] representing the difference of this set and [other].
     *
     * This is all elements in `this` that are not in `other`.
     *
     * @param other Another [CharSet].
     * @return A new [CharSet] containing elements of `this` minus those in [other].
     */
    infix fun difference(other: CharSet): CharSet {
        // Computes the difference of two sets.
        // May be replaced with a more efficient implementation in the future.
        return this intersect other.invert()
    }

    /**
     * Determines whether this set is a subset of [other].
     *
     * @param other Another [CharSet].
     * @return `true` if every element of `this` is contained in [other].
     */
    infix fun isSubsetOf(other: CharSet): Boolean {
        // Checks whether this set is a subset of another set.
        // May be replaced with a more efficient implementation in the future.
        return (this intersect other) == this
    }

    /**
     * Determines whether this set is a superset of [other].
     *
     * @param other Another [CharSet].
     * @return `true` if every element of [other] is contained in `this`.
     */
    infix fun isSupersetOf(other: CharSet): Boolean {
        // Checks whether this set is a superset of another set.
        // May be replaced with a more efficient implementation in the future.
        return (this intersect other) == other
    }

    /**
     * Determines whether this set and [other] have no elements in common.
     *
     * @param other Another [CharSet].
     * @return `true` if `(this intersect other)` is empty.
     */
    infix fun isDisjointWith(other: CharSet): Boolean {
        // Checks whether this set is disjoint with another set.
        // May be replaced with a more efficient implementation in the future.
        return (this intersect other).isEmpty()
    }

    /**
     * Determines whether this set and [other] have any overlap in characters.
     *
     * @param other Another [CharSet].
     * @return `true` if `(this intersect other)` is non-empty.
     */
    infix fun isOverlappingWith(other: CharSet): Boolean {
        // Checks whether this set is overlapping with another set.
        // May be replaced with a more efficient implementation in the future.
        return (this intersect other).isNotEmpty()
    }

    /**
     * Determines whether this set is a proper subset of [other] — i.e. strictly contained.
     *
     * @param other Another [CharSet].
     * @return `true` if `[this]` is contained by `[other]` and `[this] != [other]`.
     */
    infix fun isProperSubsetOf(other: CharSet): Boolean {
        // Checks whether this set is a proper subset of another set.
        // May be replaced with a more efficient implementation in the future.
        return (this intersect other) == this && this != other
    }

    /**
     * Determines whether this set is a proper superset of [other] — i.e. strictly contains it.
     *
     * @param other Another [CharSet].
     * @return `true` if `[this]` contains `[other]` and `[this] != [other]`.
     */
    infix fun isProperSupersetOf(other: CharSet): Boolean {
        // Checks whether this set is a proper superset of another set.
        // May be replaced with a more efficient implementation in the future.
        return (this intersect other) == other && this != other
    }

    /**
     * Returns the inverse of this set (same as [invert]).
     *
     * This operator is equivalent to `!this`.
     */
    operator fun not(): CharSet = this.invert()

    /**
     * Returns the union of this set and [other].
     *
     * Operator equivalent of [union].
     */
    operator fun plus(other: CharSet): CharSet = this union other

    /**
     * Returns the difference of this set and [other].
     *
     * Operator equivalent of [difference].
     */
    operator fun minus(other: CharSet): CharSet = this difference other

    /**
     * Returns the intersection of this set and [other].
     *
     * Operator equivalent of [intersect].
     */
    operator fun times(other: CharSet): CharSet = this intersect other

    /**
     * Converts this [CharSet] to a standard [Set] of characters.
     *
     * @return A [Set] containing every character of this [CharSet].
     */
    fun toSet(): Set<Char> {
        val result = mutableSetOf<Char>()
        val rangeCount = set.size / 2
        for (rangeIndex in 0 until rangeCount) {
            val first = set[2 * rangeIndex]
            val last = set[2 * rangeIndex + 1]
            for (c in first..last) result.add(c)
        }
        return result
    }

    /**
     * Converts this [CharSet] to a [List] of characters in ascending order.
     *
     * @return A [List] containing all characters in sorted order.
     */
    fun toList(): List<Char> {
        val result = ArrayList<Char>(size)
        val rangeCount = set.size / 2
        for (rangeIndex in 0 until rangeCount) {
            val first = set[2 * rangeIndex]
            val last = set[2 * rangeIndex + 1]
            for (c in first..last) result.add(c)
        }
        return result
    }

    /**
     * Converts this [CharSet] to a [List] of [CharRange] objects, each representing
     * a disjoint interval.
     *
     * @return A list of ranges `[start..end, start2..end2, ...]`.
     */
    fun toRangeList(): List<CharRange> {
        val rangeCount = set.size / 2
        val result = ArrayList<CharRange>(rangeCount)
        for (rangeIndex in 0 until rangeCount) {
            val first = set[2 * rangeIndex]
            val last = set[2 * rangeIndex + 1]
            result.add(first..last)
        }
        return result
    }

    /**
     * Converts this [CharSet] to a [CharArray] containing all characters in sorted order.
     *
     * @return A [CharArray] of every character in this set.
     */
    fun toCharArray(): CharArray {
        val result = CharArray(size)
        var i = 0
        val rangeCount = set.size / 2
        for (rangeIndex in 0 until rangeCount) {
            val first = set[2 * rangeIndex]
            val last = set[2 * rangeIndex + 1]
            for (c in first..last) result[i++] = c
        }
        return result
    }

    /**
     * Returns an iterator over all characters in this [CharSet], in ascending order.
     *
     * @return An [Iterator] of [Char].
     */
    operator fun iterator(): Iterator<Char> {
        return object : Iterator<Char> {
            var i = 0
            var j = 0
            override fun hasNext(): Boolean {
                return i < set.size
            }
            override fun next(): Char {
                val first = set[i]
                val last = set[i + 1]

                val c = first + j
                j++
                if (j >= last - first + 1) {
                    i += 2
                    j = 0
                }
                return c
            }
        }
    }

    /**
     * Applies the given [action] to each character in this [CharSet], in ascending order.
     *
     * @param action A function to be invoked on each character in this set.
     */
    fun forEach(action: (Char) -> Unit) {
        val rangeCount = set.size / 2
        for (rangeIndex in 0 until rangeCount) {
            val first = set[2 * rangeIndex]
            val last = set[2 * rangeIndex + 1]
            for (c in first..last) action(c)
        }
    }

    /**
     * Returns a string representation of this set in bracketed format, e.g. `[a-d0-9]`.
     * For each pair of `[start, end]`, the representation includes either
     * a single character if `start == end`, or a range `start-end` otherwise.
     *
     * @return A human-readable string representation of this [CharSet].
     */
    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("[")
        for (i in 0 until set.size step 2) {
            val first = set[i]
            val last = set[i + 1]
            if (first == last) {
                sb.append(first.formatChar())
            } else {
                sb.append(first.formatChar())
                sb.append("-")
                sb.append(last.formatChar())
            }
        }
        sb.append("]")
        return sb.toString()
    }

    companion object {
        /**
         * A private builder class used to accumulate `[start, end]` pairs.
         */
        private class Builder {
            private val set = mutableListOf<Char>()

            /**
             * Adds a non-adjacent range [c] to this builder’s internal list,
             * asserting that it does not overlap or even touch the previously
             * added range.
             */
            fun addNonAdjacent(c: CharRange) = addNonAdjacent(c.first, c.last)

            /**
             * Internal helper for adding a strictly non-adjacent range.
             */
            fun addNonAdjacent(cFirst: Char, cLast: Char) {
                if (set.isEmpty()) {
                    set.add(cFirst)
                    set.add(cLast)
                    return
                }

                val lastLast = set[set.size - 1]
                assert(lastLast.code + 1 < cFirst.code)
                set.add(cFirst)
                set.add(cLast)

                assertValidRangeList(set)
            }

            /**
             * Adds a range [c] that may be adjacent to the previous range,
             * merging them if needed.
             */
            fun addPossiblyAdjacent(c: CharRange) = addPossiblyAdjacent(c.first, c.last)

            /**
             * Internal helper for adding a possibly adjacent range,
             * merging it with the last range if they touch.
             */
            fun addPossiblyAdjacent(cFirst: Char, cLast: Char) {
                if (set.isEmpty()) {
                    set.add(cFirst)
                    set.add(cLast)
                    return
                }

                val lastFirst = set[set.size - 2]
                val lastLast = set[set.size - 1]
                assert(lastLast < cFirst)

                if (lastLast.code + 1 == cFirst.code) {
                    set[set.size - 1] = cLast
                } else {
                    set.add(cFirst)
                    set.add(cLast)
                }

                assertValidRangeList(set)
            }

            /**
             * Adds a range [c] that may overlap or adjoin the previous
             * range, merging them into a single interval if necessary.
             */
            fun addPossiblyOverlapping(c: CharRange) = addPossiblyOverlapping(c.first, c.last)

            /**
             * Internal helper for adding a possibly overlapping range.
             */
            fun addPossiblyOverlapping(cFirst: Char, cLast: Char) {
                assert(cFirst <= cLast)

                if (set.isEmpty()) {
                    set.add(cFirst)
                    set.add(cLast)
                    return
                }

                val lastFirst = set[set.size - 2]
                val lastLast = set[set.size - 1]

                assert(lastFirst <= cFirst)

                if (lastLast.code + 1 >= cFirst.code) {
                    set[set.size - 1] = maxOf(lastLast, cLast)
                } else {
                    set.add(cFirst)
                    set.add(cLast)
                }

                assertValidRangeList(set)
            }

            /**
             * Builds a new [CharSet] from the accumulated ranges.
             *
             * @return A [CharSet] representing the merged ranges.
             */
            fun build(): CharSet = CharSet(set.toCharArray())
        }

        /**
         * Indicates whether JVM-level assertions are enabled for this class.
         */
        @JvmStatic
        private val assertionStatus = CharSet::class.java.desiredAssertionStatus()

        /**
         * Asserts that the provided [list] of `[start, end, ...]` pairs forms a valid range list.
         * Checks that `start <= end` and that each `[end]` is strictly less than the next `[start]`.
         */
        @JvmStatic
        fun assertValidRangeList(list: CharArray) {
            if (!assertionStatus) return
            assert(list.size % 2 == 0)
            val rangeCount = list.size / 2
            for (i in 0 until rangeCount) {
                val start = list[2 * i]
                val end = list[2 * i + 1]
                assert(start <= end)
                if (i > 0) {
                    val prevEnd = list[2 * (i - 1) + 1]
                    assert(prevEnd < start)
                }
            }
        }

        /**
         * Overload of [assertValidRangeList] that operates on a [MutableList] of `Char`.
         */
        @JvmStatic
        fun assertValidRangeList(list: MutableList<Char>) {
            if (!assertionStatus) return
            assert(list.size % 2 == 0)
            val rangeCount = list.size / 2
            for (i in 0 until rangeCount) {
                val start = list[2 * i]
                val end = list[2 * i + 1]
                assert(start <= end)
                if (i > 0) {
                    val prevEnd = list[2 * (i - 1) + 1]
                    assert(prevEnd < start)
                }
            }
        }

        /**
         * An empty [CharSet], containing zero characters.
         */
        val none: CharSet = CharSet(CharArray(0))

        /**
         * A [CharSet] containing all characters from [Char.MIN_VALUE] to [Char.MAX_VALUE].
         */
        val all: CharSet = CharSet(charArrayOf(Char.MIN_VALUE, Char.MAX_VALUE))

        /**
         * Returns a [CharSet] containing exactly one character [c].
         *
         * @param c The single character in the resulting set.
         * @return A [CharSet] of size one.
         */
        fun one(c: Char): CharSet = CharSet(charArrayOf(c, c))

        /**
         * Returns a [CharSet] containing the inclusive range `from..to`.
         *
         * @param from The lower bound of the range.
         * @param to The upper bound of the range.
         * @throws IllegalArgumentException if `from > to`.
         */
        fun range(from: Char, to: Char): CharSet {
            require(from <= to)
            return CharSet(charArrayOf(from, to))
        }

        /**
         * Returns a [CharSet] containing any number of discrete characters [chars].
         *
         * This will merge consecutive characters into a single range for efficiency.
         */
        fun of(vararg chars: Char): CharSet {
            when (chars.size) {
                0 -> return none
                1 -> return one(chars[0])
                else -> return unsafeFromChars(chars)
            }
        }

        /**
         * Returns a [CharSet] containing the inclusive [CharRange] [range].
         */
        fun of(range: CharRange): CharSet {
            require(range.first <= range.last)
            if (range.isEmpty()) return none
            return CharSet(charArrayOf(range.first, range.last))
        }

        /**
         * Returns a [CharSet] built from multiple ranges, merging them where necessary.
         *
         * @param range Vararg list of ranges to include in the set.
         * @throws IllegalArgumentException if any range is empty or if `first > last`.
         */
        fun of(vararg range: CharRange): CharSet {
            require(range.all { it.first <= it.last })
            range.sortBy { it.first }
            val builder = Builder()
            for (r in range) {
                builder.addPossiblyOverlapping(r)
            }
            return builder.build()
        }

        /**
         * Returns a [CharSet] containing the characters in the given [String] [s].
         *
         * Merges consecutive characters in alphabetical order.
         */
        fun of(s: String): CharSet = when (s.length) {
            0 -> none
            1 -> one(s[0])
            else -> unsafeFromChars(s.toCharArray())
        }

        /**
         * Returns a [CharSet] containing characters in the given collection [s].
         */
        fun of(s: Collection<Char>): CharSet = when (s.size) {
            0 -> none
            1 -> one(s.iterator().next())
            else -> unsafeFromChars(s.toCharArray())
        }

        /**
         * Returns a [CharSet] by concatenating multiple strings [s] and merging their characters.
         */
        fun of(vararg s: String): CharSet = of(s.joinToString(""))

        /**
         * Returns the union of multiple [CharSet] instances [s].
         */
        fun union(vararg s: CharSet): CharSet {
            var result = none
            for (set in s) {
                result = result union set
            }
            return result
        }

        /**
         * Returns the union of all the [CharSet] elements in a collection [s].
         */
        fun union(s: Collection<CharSet>): CharSet {
            var result = none
            for (set in s) {
                result = result union set
            }
            return result
        }

        /**
         * Internal helper to build a [CharSet] from an unsorted array of characters,
         * merging consecutive duplicates and adjacent runs.
         */
        private fun unsafeFromChars(chars: CharArray): CharSet {
            when (chars.size) {
                0 -> return none
                1 -> return one(chars[0])
                else -> {
                    Arrays.sort(chars)

                    val ranges = mutableListOf<Char>()
                    var start = chars[0]
                    var end = start
                    for (i in 1 until chars.size) {
                        val c = chars[i]

                        if (c == end) continue

                        if (c == end + 1) {
                            end = c
                            continue
                        }

                        ranges.add(start)
                        ranges.add(end)
                        start = c
                        end = c
                    }
                    ranges.add(start)
                    ranges.add(end)

                    return CharSet(ranges.toCharArray())
                }
            }
        }

        /**
         * A [CharSet] of ASCII characters from 0 to 127.
         */
        val ascii = of(0.toChar()..127.toChar())

        /**
         * A [CharSet] of ASCII digit characters (`0-9`).
         */
        val digit: CharSet = ascii.filter { it.isDigit() }

        /**
         * A [CharSet] of ASCII letters (`A-Za-z`).
         */
        val letter: CharSet = ascii.filter { it.isLetter() }

        /**
         * A [CharSet] of ASCII alphanumeric characters.
         */
        val letterOrDigit: CharSet = ascii.filter { it.isLetterOrDigit() }

        /**
         * A [CharSet] of hexadecimal digits (`0-9A-Fa-f`).
         */
        val hexDigit = of("01234567890abcdefABCDEF")

        /**
         * A [CharSet] of ASCII whitespace characters.
         */
        val whitespace = ascii.filter { it.isWhitespace() }

        /**
         * A [CharSet] containing all defined Unicode characters.
         */
        val validUnicode: CharSet = of((Char.MIN_VALUE..Char.MAX_VALUE).filter { it.isDefined() })

        /**
         * A [CharSet] containing all Unicode digit characters.
         */
        val unicodeDigit: CharSet = of((Char.MIN_VALUE..Char.MAX_VALUE).filter { it.isDigit() })

        /**
         * A [CharSet] containing all Unicode letters.
         */
        val unicodeLetter: CharSet = of((Char.MIN_VALUE..Char.MAX_VALUE).filter { it.isLetter() })

        /**
         * A [CharSet] containing all Unicode letters or digits.
         */
        val unicodeLetterOrDigit: CharSet = of((Char.MIN_VALUE..Char.MAX_VALUE).filter { it.isLetterOrDigit() })

        /**
         * A [CharSet] containing various forms of Unicode whitespace,
         * as well as common ASCII whitespace chars.
         */
        val unicodeWhitespace = of((Char.MIN_VALUE..Char.MAX_VALUE).filter { it.isWhitespace() || it == '\n' || it == '\r' || it == ' ' })
    }
}

/**
 * Represents a top-level partitioning of the entire `Char.MIN_VALUE..Char.MAX_VALUE` range
 * into adjacent [CharRange] segments.
 *
 * For example, a [CharSetTop] might partition the space into `[0..9] [10..31] [32..127] [128..65535]`.
 * The [basis] list covers the entire range from [Char.MIN_VALUE] to [Char.MAX_VALUE] in consecutive
 * chunks with no gaps or overlaps.
 *
 * @property basis A list of consecutive [CharRange] partitions fully covering `Char.MIN_VALUE..Char.MAX_VALUE`.
 */
data class CharSetTop(val basis: List<CharRange>) {
    init {
        if (this.javaClass.desiredAssertionStatus()) {
            assert(basis.all { it.first <= it.last })
            assert(basis.zipWithNext().all { it.first.last == it.second.first - 1 })
            assert(basis.size > 0)
            assert(basis.first().first == Char.MIN_VALUE)
            assert(basis.last().last == Char.MAX_VALUE)
            // Basis fully partitions the space between Char.MIN_VALUE and Char.MAX_VALUE.
        }
    }

    /**
     * The total number of basis partitions.
     */
    val size = basis.size

    /**
     * Produces a refined partition of this [CharSetTop] combined with [other],
     * effectively computing the partition that differentiates all cut points
     * in both [basis] lists.
     *
     * @param other Another [CharSetTop].
     * @return A new [CharSetTop] whose basis includes all cut boundaries of both.
     */
    fun refine(other: CharSetTop): CharSetTop {
        val builder = Builder()

        // Cut indices.
        fun CharSetTop.getCut(i: Int): Int {
            val r = this.basis[i.div(2)]
            return if (i % 2 == 0) r.first.code else r.last.code + 1
        }

        var i = 0
        var j = 0
        var lastCut = Char.MIN_VALUE.code
        while (i < 2 * this.basis.size && j < 2 * other.basis.size) {
            val x = this.getCut(i)
            val y = other.getCut(j)

            if (x < y) {
                if (lastCut < x) {
                    builder.cut(x)
                    lastCut = x
                }
                i++
            } else if (x == y) {
                if (lastCut < x) {
                    builder.cut(x)
                    lastCut = x
                }
                i++
                j++
            } else {
                if (lastCut < y) {
                    builder.cut(y)
                    lastCut = y
                }
                j++
            }
        }

        return builder.finish()
    }

    /**
     * Produces a refined partition by combining this [CharSetTop] with a [CharSet].
     * Internally uses [fromSet] to transform the [CharSet] into a [CharSetTop] and
     * then calls [refine].
     *
     * @param set A [CharSet] whose cut points will also become partitions.
     * @return A new [CharSetTop].
     */
    fun refine(set: CharSet) = refine(fromSet(set))

    /**
     * Returns a string representation of all partitions in this top-level set,
     * in a bracket-like notation. For example: `[\\u0000-\\u0009\\u000A-\\u001F\\u0020-\\u007F...]`.
     */
    override fun toString(): String {
        return basis.joinToString("", "[", "]") {
            if (it.first == it.last) it.first.formatChar()
            else "${it.first.formatChar()}-${it.last.formatChar()}"
        }
    }

    companion object {
        /**
         * A trivial [CharSetTop] with a single range covering `Char.MIN_VALUE..Char.MAX_VALUE`.
         */
        val trivial = CharSetTop(listOf(Char.MIN_VALUE..Char.MAX_VALUE))

        /**
         * A private builder to handle the logic of cutting intervals.
         */
        private class Builder {
            val list: MutableList<CharRange> = mutableListOf()

            /**
             * Cuts the current set of intervals at [code], effectively inserting
             * a boundary between `[last.last+1 .. code-1]`.
             */
            fun cut(code: Int) {
                require(code > Char.MIN_VALUE.code)
                require(code <= Char.MAX_VALUE.code + 1)

                if (list.isEmpty()) {
                    list.add(Char.MIN_VALUE..(code - 1).toChar())
                    return
                }

                val last = list.last()
                require(last.last + 1 <= (code - 1).toChar())
                list.add(last.last + 1..(code - 1).toChar())
            }

            /**
             * Finishes building the partition, ensuring it covers up to [Char.MAX_VALUE].
             *
             * @return A new [CharSetTop] covering `Char.MIN_VALUE..Char.MAX_VALUE` with the
             * specified intermediate cut points.
             */
            fun finish(): CharSetTop {
                if (list.isEmpty()) {
                    list.add(Char.MIN_VALUE..Char.MAX_VALUE)
                    return CharSetTop(list)
                }

                val last = list.last()
                if (last.last != Char.MAX_VALUE) {
                    list.add(last.last + 1..Char.MAX_VALUE)
                }
                return CharSetTop(list)
            }
        }

        /**
         * Produces a [CharSetTop] from a given [CharSet], turning its non-consecutive ranges
         * into cut points in the `Char.MIN_VALUE..Char.MAX_VALUE` space.
         *
         * @param set A [CharSet] whose boundaries become cuts in the resulting [CharSetTop].
         * @return A new [CharSetTop].
         */
        fun fromSet(set: CharSet): CharSetTop {
            val builder = Builder()

            val ranges = set.toRangeList()

            // Cut indices.
            fun getCut(i: Int): Int {
                val r = ranges[i.div(2)]
                return if (i % 2 == 0) r.first.code else r.last.code + 1
            }

            for (i in 0 until 2 * set.nonConsecutiveRangeCount) {
                val x = getCut(i)
                if (x == Char.MIN_VALUE.code) continue
                builder.cut(x)
            }

            return builder.finish()
        }
    }
}
