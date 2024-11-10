package one.wabbit.parsing.charset

import one.wabbit.formatting.escapeJavaChar
import java.util.*
import kotlin.collections.ArrayList

private inline fun Char.formatChar() = escapeJavaChar(this)

class CharSet private constructor(private val set: CharArray) {
    init {
        assertValidRangeList(set)
    }

    private var hashCodeAndSize: ULong = 0x00000000FFFFFFFFUL
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

    val nonConsecutiveRangeCount: Int = set.size / 2
    val size: Int get() = (ensureHashCodeAndSizeAreComputed() and 0xFFFFFFFFUL).toInt()
    override fun hashCode(): Int = (ensureHashCodeAndSizeAreComputed() shr 32).toInt()

    override fun equals(other: Any?): Boolean {
        if (other !is CharSet) return false
        return this.set.contentEquals(other.set)
    }

    fun isEmpty(): Boolean = set.isEmpty()
    fun isNotEmpty(): Boolean = set.isNotEmpty()

    fun isAll(): Boolean =
        set.size == 2 && set[0] == Char.MIN_VALUE && set[1] == Char.MAX_VALUE

    // Indexing
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

    // Sampling
    fun sample(random: SplittableRandom): Char = this[random.nextInt(size)]
    fun sample(random: Random): Char = this[random.nextInt(size)]

    // Containment
    operator fun contains(c: Char): Boolean {
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

    fun containsAll(cs: Collection<Char>): Boolean {
        for (c in cs) {
            if (c !in this) return false
        }
        return true
    }

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

    // Transformations
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

    // Operations
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

    infix fun difference(other: CharSet): CharSet {
        // Computes the difference of two sets.
        // May be replaced with a more efficient implementation in the future.
        return this intersect other.invert()
    }

    infix fun isSubsetOf(other: CharSet): Boolean {
        // Checks whether this set is a subset of another set.
        // May be replaced with a more efficient implementation in the future.
        return (this intersect other) == this
    }

    infix fun isSupersetOf(other: CharSet): Boolean {
        // Checks whether this set is a superset of another set.
        // May be replaced with a more efficient implementation in the future.
        return (this intersect other) == other
    }

    infix fun isDisjointWith(other: CharSet): Boolean {
        // Checks whether this set is disjoint with another set.
        // May be replaced with a more efficient implementation in the future.
        return (this intersect other).isEmpty()
    }

    infix fun isOverlappingWith(other: CharSet): Boolean {
        // Checks whether this set is overlapping with another set.
        // May be replaced with a more efficient implementation in the future.
        return (this intersect other).isNotEmpty()
    }

    infix fun isProperSubsetOf(other: CharSet): Boolean {
        // Checks whether this set is a proper subset of another set.
        // May be replaced with a more efficient implementation in the future.
        return (this intersect other) == this && this != other
    }

    infix fun isProperSupersetOf(other: CharSet): Boolean {
        // Checks whether this set is a proper superset of another set.
        // May be replaced with a more efficient implementation in the future.
        return (this intersect other) == other && this != other
    }

    // Operators
    operator fun not(): CharSet = this.invert()
    operator fun plus(other: CharSet): CharSet = this union other
    operator fun minus(other: CharSet): CharSet = this difference other
    operator fun times(other: CharSet): CharSet = this intersect other

    // Conversions
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

    fun forEach(action: (Char) -> Unit) {
        val rangeCount = set.size / 2
        for (rangeIndex in 0 until rangeCount) {
            val first = set[2 * rangeIndex]
            val last = set[2 * rangeIndex + 1]
            for (c in first..last) action(c)
        }
    }

    // String representations
    override fun toString(): String {
        if (set.isEmpty()) return "[]"
        if (set.size == 1) {
            val first = set[0]
            val last = set[1]
            return if (first == last) "[${first.formatChar()}]" else "[${first.formatChar()}-${last.formatChar()}]"
        }

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
        private class Builder {
            private val set = mutableListOf<Char>()

            fun addNonAdjacent(c: CharRange) = addNonAdjacent(c.first, c.last)

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

            fun addPossiblyAdjacent(c: CharRange) = addPossiblyAdjacent(c.first, c.last)

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

            fun addPossiblyOverlapping(c: CharRange) = addPossiblyOverlapping(c.first, c.last)

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

            fun build(): CharSet = CharSet(set.toCharArray())
        }

        @JvmStatic
        private val assertionStatus = this.javaClass.desiredAssertionStatus()

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

        val none: CharSet = CharSet(CharArray(0))

        val all: CharSet = CharSet(charArrayOf(Char.MIN_VALUE, Char.MAX_VALUE))

        fun one(c: Char): CharSet = CharSet(charArrayOf(c, c))

        fun range(from: Char, to: Char): CharSet {
            require(from <= to)
            return CharSet(charArrayOf(from, to))
        }

        fun of(vararg chars: Char): CharSet {
            when (chars.size) {
                0 -> return none
                1 -> return one(chars[0])
                else -> return unsafeFromChars(chars)
            }
        }

        fun of(range: CharRange): CharSet {
            require(range.first <= range.last)
            if (range.isEmpty()) return none
            return CharSet(charArrayOf(range.first, range.last))
        }

        fun of(vararg range: CharRange): CharSet {
            require(range.all { it.first <= it.last })
            range.sortBy { it.first }
            val builder = Builder()
            for (r in range) {
                builder.addPossiblyOverlapping(r)
            }
            return builder.build()
        }

        fun of(s: String): CharSet = when (s.length) {
            0 -> none
            1 -> one(s[0])
            else -> unsafeFromChars(s.toCharArray())
        }

        fun of(s: Collection<Char>): CharSet = when (s.size) {
            0 -> none
            1 -> one(s.iterator().next())
            else -> unsafeFromChars(s.toCharArray())
        }

        fun of(vararg s: String): CharSet = of(s.joinToString(""))

        fun union(vararg s: CharSet): CharSet {
            var result = none
            for (set in s) {
                result = result union set
            }
            return result
        }

        fun union(s: Collection<CharSet>): CharSet {
            var result = none
            for (set in s) {
                result = result union set
            }
            return result
        }

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

        val ascii = of(0.toChar()..127.toChar())
        val digit: CharSet = ascii.filter { it.isDigit() }
        val letter: CharSet = ascii.filter { it.isLetter() }
        val letterOrDigit: CharSet = ascii.filter { it.isLetterOrDigit() }
        val hexDigit = of("01234567890abcdefABCDEF")
        val whitespace = ascii.filter { it.isWhitespace() }

        val validUnicode: CharSet = of((Char.MIN_VALUE..Char.MAX_VALUE).filter { it.isDefined() })
        val unicodeDigit: CharSet = of((Char.MIN_VALUE..Char.MAX_VALUE).filter { it.isDigit() })
        val unicodeLetter: CharSet = of((Char.MIN_VALUE..Char.MAX_VALUE).filter { it.isLetter() })
        val unicodeLetterOrDigit: CharSet = of((Char.MIN_VALUE..Char.MAX_VALUE).filter { it.isLetterOrDigit() })
        val unicodeWhitespace = of((Char.MIN_VALUE..Char.MAX_VALUE).filter { it.isWhitespace() || it == '\n' || it == '\r' || it == ' ' })
    }
}

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

    // The number of basis elements.
    val size = basis.size

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

    fun refine(set: CharSet) = refine(fromSet(set))

    override fun toString(): String {
        return basis.joinToString("", "[", "]") {
            if (it.first == it.last) it.first.formatChar()
            else "${it.first.formatChar()}-${it.last.formatChar()}"
        }
    }

    companion object {
        val trivial = CharSetTop(listOf(Char.MIN_VALUE..Char.MAX_VALUE))

        private class Builder {
            val list: MutableList<CharRange> = mutableListOf()

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
