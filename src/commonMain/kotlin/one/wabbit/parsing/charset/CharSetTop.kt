package one.wabbit.parsing.charset

/**
 * Represents a top-level partitioning of the entire `Char.MIN_VALUE..Char.MAX_VALUE` range into
 * adjacent [CharRange] segments.
 *
 * For example, a [CharSetTop] might partition the space into `[0..9] [10..31] [32..127]
 * [128..65535]`. The [basis] list covers the entire range from [Char.MIN_VALUE] to [Char.MAX_VALUE]
 * in consecutive chunks with no gaps or overlaps.
 *
 * @property basis A list of consecutive [CharRange] partitions fully covering
 *   `Char.MIN_VALUE..Char.MAX_VALUE`.
 */
data class CharSetTop(val basis: List<CharRange>) {
    init {
        check(basis.all { it.first <= it.last })
        check(basis.zipWithNext().all { it.first.last == it.second.first - 1 })
        check(basis.isNotEmpty())
        check(basis.first().first == Char.MIN_VALUE)
        check(basis.last().last == Char.MAX_VALUE)
        // Basis fully partitions the space between Char.MIN_VALUE and Char.MAX_VALUE.
    }

    /** The total number of basis partitions. */
    val size = basis.size

    /**
     * Produces a refined partition of this [CharSetTop] combined with [other], effectively
     * computing the partition that differentiates all cut points in both [basis] lists.
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
     * Produces a refined partition by combining this [CharSetTop] with a [CharSet]. Internally uses
     * [fromSet] to transform the [CharSet] into a [CharSetTop] and then calls [refine].
     *
     * @param set A [CharSet] whose cut points will also become partitions.
     * @return A new [CharSetTop].
     */
    fun refine(set: CharSet) = refine(fromSet(set))

    /**
     * Returns a string representation of all partitions in this top-level set, in a bracket-like
     * notation. For example: `[\\u0000-\\u0009\\u000A-\\u001F\\u0020-\\u007F...]`.
     */
    override fun toString(): String =
        basis.joinToString("", "[", "]") {
            if (it.first == it.last) {
                it.first.formatChar()
            } else {
                "${it.first.formatChar()}-${it.last.formatChar()}"
            }
        }

    companion object {
        /** A trivial [CharSetTop] with a single range covering `Char.MIN_VALUE..Char.MAX_VALUE`. */
        val trivial = CharSetTop(listOf(Char.MIN_VALUE..Char.MAX_VALUE))

        /** A private builder to handle the logic of cutting intervals. */
        private class Builder {
            val list: MutableList<CharRange> = mutableListOf()

            /**
             * Cuts the current set of intervals at [code], effectively inserting a boundary between
             * `[last.last+1 .. code-1]`.
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
             *   specified intermediate cut points.
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
         * Produces a [CharSetTop] from a given [CharSet], turning its non-consecutive ranges into
         * cut points in the `Char.MIN_VALUE..Char.MAX_VALUE` space.
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
