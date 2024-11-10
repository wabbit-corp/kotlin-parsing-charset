package one.wabbit.parsing.charset

import org.junit.Test
import one.wabbit.random.gen.Gen
import one.wabbit.random.gen.foreach
import one.wabbit.random.gen.foreachMin
import java.util.SplittableRandom
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CharSetSpec {
    private val miniAlphabet = run {
        val list = mutableListOf<Char>()
        list.add(Char.MIN_VALUE)
        list.add(Char.MIN_VALUE.inc())
        list.add(Char.MAX_VALUE)
        list.add(Char.MAX_VALUE.dec())
        for (i in '0'.code..'9'.code) list.add(i.toChar())
        list
    }

    private val genChar = Gen.oneOf(miniAlphabet)
    private val genString = Gen.int(0 ..< 20)
        .flatMap { Gen.repeat(it, genChar) }
        .map { it.joinToString("") }

    private val genCharSet = genString.map { CharSet.of(it) }
    private val genCharSetPair = genCharSet.zip(genCharSet)
    private val genCharSetTriple = genCharSet.zip(genCharSet).zip(genCharSet)
        .map { Triple(it.first.first, it.first.second, it.second) }

    private val genCharList = genString.map { it.toList() }

    // Equality properties
    private val properties = listOf<(CharSet) -> Any>(
        { it.hashCode() },
        { it.size },
        { it.nonConsecutiveRangeCount },
        { it.toList() },
        { it.toSet() },
        { it.toString() }
    )
    @Test fun `indiscernibility of identicals`() {
        genCharSetPair.foreach(100000) { (a, b) ->
            if (a == b) {
                for (property in properties) {
                    assertEquals(property(a), property(b))
                }
            }
        }
    }
    @Test fun `identity of indiscernibles`() {
        genCharSetPair.foreach(100000) { (a, b) ->
            val areIndiscernible = properties.all { property ->
                property(a) == property(b)
            }
            if (areIndiscernible) {
                assertEquals(a, b)
            }
        }
    }

    @Test fun `CharSet from empty list`() {
        val emptyCharSetFromEmptyList = CharSet.of(emptyList<Char>())
        val emptyCharSetFromEmptyString = CharSet.of("")
        assertEquals(CharSet.none, emptyCharSetFromEmptyList)
        assertEquals(CharSet.none, emptyCharSetFromEmptyString)
    }

    @Test fun `CharSet constructor roundtrip`() {
        genCharSet.foreach(100) { charSet ->
            assertEquals(charSet, CharSet.of(charSet.toSet()))
            assertEquals(charSet, CharSet.of(charSet.toList()))
            assertEquals(charSet, CharSet.of(*charSet.toCharArray()))
        }
    }

    @Test fun `Constructor order independence`() {
        genCharList.foreach(100) { charList ->
            assertEquals(
                CharSet.of(charList.sorted()),
                CharSet.of(charList.shuffled()))
        }
    }

//    @Test fun `Constructor regression #1`() {
//        // A..-, Z..Z, _.._, a..-, z..z
//        val a = CharSet.of('A'..'-', 'Z'..'Z', '_'..'_', 'a'..'-', 'z'..'z')
//    }

    //    operator fun contains(c: Char): Boolean
    //    fun containsAll(cs: Collection<Char>): Boolean
    //    fun containsAll(that: CharSet): Boolean
    @Test fun `contains is consistent with toSet contains`() =
        genCharSet.foreach(100) { charSet ->
            val set = charSet.toSet()
            for (ch in miniAlphabet) {
                assertEquals(set.contains(ch), ch in charSet)
            }
        }
    @Test fun `containsAll is consistent with toSet containsAll`() =
        genCharSetPair.foreach(100) { (charSet1, charSet2) ->
            val set1 = charSet1.toSet()
            val set2 = charSet2.toSet()
            assertEquals(set1.containsAll(set2), charSet1.containsAll(charSet2))
            assertEquals(set2.containsAll(set1), charSet2.containsAll(charSet1))
            assertEquals(set1.containsAll(set2), charSet1.containsAll(set2))
            assertEquals(set2.containsAll(set1), charSet2.containsAll(set1))
        }

    // fun overlap(that: CharSet): Overlap
    @Test fun `overlap regression #1`() {
        val a = CharSet.of('\u0001', '2', '5', '6', '7')
        val b = CharSet.of('1', '2')
        assertEquals(Overlap.PARTIAL, a.overlap(b))
    }
    @Test fun `overlap regression #2`() {
        // [2], [\x0102-35\ufffe-\uffff]
        val a = CharSet.of('2')
        val b = CharSet.of('\u0001', '0', '2', '3', '5', '\uFFFE', '\uFFFF')
        assertEquals(Overlap.SECOND_CONTAINS_FIRST, a.overlap(b))
    }
    @Test fun `overlap regression #3`() {
        // [58], [\x0115-68\ufffe-\uffff]
        val a = CharSet.of('5', '8')
        val b = CharSet.of('\u0001', '1', '5', '6', '8', '\uFFFE', '\uFFFF')
        assertEquals(Overlap.SECOND_CONTAINS_FIRST, a.overlap(b))
    }
    @Test fun `overlap regression #4`() {
        // [246-79], [\x0124-79]
        val a = CharSet.of('2', '4', '6', '7', '8', '9')
        val b = CharSet.of('\u0001', '2', '4', '5', '6', '7', '8', '9')
        assertEquals(Overlap.SECOND_CONTAINS_FIRST, a.overlap(b))
    }
    @Test fun `overlap regression #5`() {
        // [\x008], [8]
        val a = CharSet.of('\u0000', '8')
        val b = CharSet.of('8')
        assertEquals(Overlap.FIRST_CONTAINS_SECOND, a.overlap(b))
    }
    @Test fun `overlap is consistent with default implementation`() {
        genCharSetPair.foreachMin(SplittableRandom(), 100) { (a, b) ->
            val overlap = a.overlap(b)
            // A /\ B == none   -> Overlap.EMPTY
            // A /\ B == A      -> Overlap.SECOND_CONTAINS_FIRST
            // A /\ B == B      -> Overlap.FIRST_CONTAINS_SECOND
            // A /\ B == A \/ B -> Overlap.EQUAL
            // else             -> Overlap.PARTIAL

            val default = when {
                (a intersect b) == CharSet.none -> Overlap.EMPTY
                (a intersect b) == a -> Overlap.SECOND_CONTAINS_FIRST
                (a intersect b) == b -> Overlap.FIRST_CONTAINS_SECOND
                (a intersect b) == (a union b) -> Overlap.EQUAL
                else -> Overlap.PARTIAL
            }

            assertEquals(default, overlap, "Overlap should be consistent with default implementation; $a, $b")
        }
    }

    // val size: Int
    // fun isEmpty(): Boolean
    // fun isNotEmpty(): Boolean
    // fun isAll(): Boolean
    val allSize = CharSet.all.size
    @Test fun `size returns 0 for CharSet none`() =
        assertTrue(CharSet.none.size == 0)
    @Test fun `size is consistent with toSet size`() =
        genCharSet.foreach(100) { charSet ->
            assertEquals(charSet.toSet().size, charSet.size)
        }
    @Test fun `isEmpty is consistent with size`() =
        genCharSet.foreach(100) { charSet ->
            assertEquals(charSet.size == 0, charSet.isEmpty())
        }
    @Test fun `isNotEmpty is consistent with isEmpty`() =
        genCharSet.foreach(100) { charSet ->
            assertEquals(!charSet.isEmpty(), charSet.isNotEmpty())
        }
    @Test fun `isAll returns true for all`() =
        assertTrue(CharSet.all.isAll())
    @Test fun `isAll returns false for smaller sets`() =
        genCharSet.foreach(100) { charSet ->
            assertEquals(charSet.size == allSize, charSet.isAll())
        }

    // fun toSet(): Set<Char>
    // fun toList(): List<Char>
    @Test fun `toSet is consistent with toList`() =
        genCharSet.foreach(100) { charSet ->
            assertEquals(charSet.toList().toSet(), charSet.toSet())
        }

    // fun toCharArray(): CharArray
    @Test fun `toCharArray is consistent with toList`() =
        genCharSet.foreach(100) { charSet ->
            assertEquals(charSet.toList(), charSet.toCharArray().toList())
        }

    // operator fun iterator(): Iterator<Char>
    @Test fun `iterator completeness`() {
        genCharSet.foreach(100) { charSet ->
            val iterator = charSet.iterator()
            val iteratedChars = mutableListOf<Char>()
            while (iterator.hasNext()) {
                iteratedChars.add(iterator.next())
            }
            val charSetAsList = charSet.toSet().toList()
            assertEquals(charSetAsList.size, iteratedChars.size, "Iterator should traverse all characters")
            assertTrue(charSetAsList.containsAll(iteratedChars), "Iterator should traverse each character exactly once")
            assertTrue(iteratedChars.containsAll(charSetAsList), "Iterator should traverse each character exactly once")
        }
    }
    @Test fun `iterator idempotence`() {
        genCharSet.foreach(100) { charSet ->
            val iterator1 = charSet.iterator()
            val iterator2 = charSet.iterator()

            while (iterator1.hasNext() && iterator2.hasNext()) {
                val charFromIterator1 = iterator1.next()
                val charFromIterator2 = iterator2.next()
                assertEquals(charFromIterator1, charFromIterator2, "Iterators should traverse CharSet in the same order")
            }

            // After traversing, both iterators should be exhausted
            assertFalse(iterator1.hasNext(), "First iterator should be exhausted")
            assertFalse(iterator2.hasNext(), "Second iterator should be exhausted")
        }
    }

    // fun forEach(action: (Char) -> Unit)
    @Test fun `forEach is equivalent to toList`() {
        genCharSet.foreach(100) { charSet ->
            val chars = mutableListOf<Char>()
            charSet.forEach { chars.add(it) }
            assertEquals(charSet.toList(), chars)
        }
    }

    // operator fun get(index: Int): Char
    @Test fun `get is consistent with toList get`() {
        genCharSet.foreach(100) { charSet ->
            val chars = charSet.toList()
            for (i in chars.indices) {
                assertEquals(chars[i], charSet[i], "Character at index $i should match")
            }
        }
    }
    @Test(expected = IndexOutOfBoundsException::class)
    fun `Accessing by invalid index throws IndexOutOfBoundsException`() {
        genCharSet.foreach(10) { charSet ->
            val size = charSet.size
            // Attempt to access an index one past the last valid index
            charSet[size]
        }
    }

    //    fun map(transform: (Char) -> Char): CharSet
    @Test fun `mapping with identity preserves CharSet`() {
        genCharSet.foreach(100) { charSet ->
            val mappedCharSet = charSet.map { it }
            assertEquals(charSet, mappedCharSet, "Mapping with identity should preserve CharSet")
        }
    }
    @Test fun `mapping with constant function yields CharSet of one element`() {
        genCharSet.foreach(100) { charSet ->
            if (charSet.isEmpty()) {
                val mappedCharSet = charSet.map { 'a' }
                assertEquals(CharSet.none, mappedCharSet)
            } else {
                val mappedCharSet = charSet.map { 'a' }
                assertEquals(CharSet.one('a'), mappedCharSet)
            }
        }
    }
    @Test fun `mapping with composed function is equivalent to mapping twice`() {
        genCharSet.foreach(100) { charSet ->
            val mappedCharSet1 = charSet.map { (it.code / 2).toChar() }.map { (it.code / 3).toChar() }
            val mappedCharSet2 = charSet.map {
                val x = (it.code / 2).toChar()
                (x.code / 3).toChar()
            }
            assertEquals(mappedCharSet1, mappedCharSet2, "Mapping with composed function should be equivalent to mapping twice")
        }
    }

    //    fun filter(predicate: (Char) -> Boolean): CharSet
    @Test fun `filter with {true} returns same set`() {
        genCharSet.foreach(100) { charSet ->
            val filteredCharSet = charSet.filter { true }
            assertEquals(charSet, filteredCharSet, "Filtering with a predicate that all elements satisfy should not change the CharSet")
        }
    }
    @Test fun `filter with {false} returns empty set`() {
        genCharSet.foreach(100) { charSet ->
            val filteredCharSet = charSet.filter { false }
            assertEquals(CharSet.none, filteredCharSet, "Filtering with a predicate that no elements satisfy should result in an empty CharSet")
        }
    }
    @Test fun `filter with {it in charSet} returns same set`() {
        genCharSet.foreach(100) { charSet ->
            val filteredCharSet = charSet.filter { it in charSet }
            assertEquals(charSet, filteredCharSet, "Filtering with a predicate that all elements satisfy should not change the CharSet")
        }
    }
    @Test fun `filter with {it !in charSet} returns empty set`() {
        genCharSet.foreach(100) { charSet ->
            val filteredCharSet = charSet.filter { it !in charSet }
            assertEquals(CharSet.none, filteredCharSet, "Filtering with a predicate that no elements satisfy should result in an empty CharSet")
        }
    }
    @Test fun `filter is consistent with toList filter`() {
        genCharSetPair.foreach(100) { (charSet1, charSet2) ->
            val filteredCharSet = charSet1.filter { it in charSet2 }.toList()
            val filteredList = charSet1.toList().filter { it in charSet2 }
            assertEquals(filteredList, filteredCharSet, "Filtering should be consistent with toList filter")
        }
    }
    @Test fun `filter is consistent with intersect filter`() =
        genCharSetPair.foreach { (a, b) ->
            assertEquals(a.intersect(b), a.filter { it in b })
        }
    @Test fun `filter composition`() {
        genCharSetTriple.foreach(100) { (charSet1, charSet2, charSet3) ->
            val filteredCharSet1 = charSet1.filter { it in charSet2 }.filter { it in charSet3 }
            val filteredCharSet2 = charSet1.filter { it in charSet2 && it in charSet3 }
            assertEquals(filteredCharSet1, filteredCharSet2)
        }
    }
    @Test fun `filter and union distributivity #1`() {
        genCharSetTriple.foreach(100) { (a, b, c) ->
            assertEquals(
                a.filter { it in b || it in c },
                a.filter { it in b } union a.filter { it in c })
            assertEquals(
                (a union b).filter { it in c },
                a.filter { it in c } union b.filter { it in c })
        }
    }
    @Test fun `filter and intersect distributivity`() {
        genCharSetTriple.foreach { (a, b, c) ->
            assertEquals(
                a.filter { it in b && it in c },
                a.filter { it in b } intersect a.filter { it in c })
            assertEquals(
                (a intersect b).filter { it in c },
                a.filter { it in c } intersect b.filter { it in c })
        }
    }

    //    fun count(predicate: (Char) -> Boolean): Int
    @Test fun `count with {true} returns size`() {
        genCharSet.foreach(100) { charSet ->
            val count = charSet.count { true }
            assertEquals(charSet.size, count, "Counting with a predicate that all elements satisfy should return the size of the CharSet")
        }
    }
    @Test fun `count with {false} returns 0`() {
        genCharSet.foreach(100) { charSet ->
            val count = charSet.count { false }
            assertEquals(0, count, "Counting with a predicate that no elements satisfy should return 0")
        }
    }
    @Test fun `count with {it in charSet} returns size`() {
        genCharSet.foreach(100) { charSet ->
            val count = charSet.count { it in charSet }
            assertEquals(charSet.size, count, "Counting with a predicate that all elements satisfy should return the size of the CharSet")
        }
    }
    @Test fun `count with {it !in charSet} returns 0`() {
        genCharSet.foreach(100) { charSet ->
            val count = charSet.count { it !in charSet }
            assertEquals(0, count, "Counting with a predicate that no elements satisfy should return 0")
        }
    }
    @Test fun `count is equivalent to filter composed with size`() {
        genCharSet.foreach(100) { charSet ->
            val count = charSet.count { it in charSet }
            val filteredCharSet = charSet.filter { it in charSet }
            assertEquals(filteredCharSet.size, count, "Counting should be equivalent to filtering and then taking the size")
        }
    }
    @Test fun `count is consistent with toList count`() {
        genCharSetPair.foreach(100) { (charSet1, charSet2) ->
            val countResult = charSet1.count { it in charSet2 }
            val toListCountResult = charSet1.toList().count { it in charSet2 }
            assertEquals(toListCountResult, countResult, "Counting should be consistent with toList count")
        }
    }

    // Union properties
    // infix fun union(other: CharSet): CharSet
    @Test fun `union with none, all, and itself`() {
        genCharSet.foreach(100) { charSet ->
            assertEquals(charSet, charSet union CharSet.none)
            assertEquals(CharSet.all, charSet union CharSet.all)
            assertEquals(charSet, charSet union charSet)
        }
    }
    @Test fun `union is commutative`() {
        genCharSetPair.foreach(100) { (charSet1, charSet2) ->
            val union1 = charSet1 union charSet2
            val union2 = charSet2 union charSet1
            assertEquals(union1, union2, "Union should be commutative")
        }
    }
    @Test fun `union is associative`() {
        genCharSetTriple.foreach(100) { (charSetA, charSetB, charSetC) ->
            val unionABThenC = (charSetA union charSetB) union charSetC
            val unionAThenBC = charSetA union (charSetB union charSetC)
            assertEquals(unionABThenC, unionAThenBC, "Union should be associative")
        }
    }
    @Test fun `union is consistent with toSet union`() {
        genCharSetPair.foreach(100) { (charSetA, charSetB) ->
            val union = (charSetA union charSetB).toSet()
            val setUnion = charSetA.toSet() union charSetB.toSet()
            assertEquals(setUnion, union, "Union should be consistent with toSet union")
        }
    }
    @Test fun `consistency of containsAll with union`() {
        genCharSetTriple.foreach(100) { (a, b, c) ->
            // If a contains all elements of b and c,
            // then a should contain all elements of b union c.
            if (a.containsAll(b) && a.containsAll(c)) {
                val unionBC = b union c
                assertTrue(a.containsAll(unionBC))
            }
        }
    }

    // Intersection properties
    // infix fun intersect(other: CharSet): CharSet
    @Test fun `intersection with none, all, and itself`() {
        genCharSet.foreach(100) { charSet ->
            assertEquals(CharSet.none, charSet intersect CharSet.none)
            assertEquals(charSet, charSet intersect CharSet.all)
            assertEquals(charSet, charSet intersect charSet)
        }
    }
    @Test fun `intersection is commutative`() {
        genCharSetPair.foreach(100) { (charSet1, charSet2) ->
            val intersection1 = charSet1 intersect charSet2
            val intersection2 = charSet2 intersect charSet1
            assertEquals(intersection1, intersection2, "Intersection should be commutative")
        }
    }
    @Test fun `intersection is associative`() {
        genCharSetTriple.foreach(100) { (charSetA, charSetB, charSetC) ->
            val intersectABThenC = (charSetA intersect charSetB) intersect charSetC
            val intersectAThenBC = charSetA intersect (charSetB intersect charSetC)
            assertEquals(intersectABThenC, intersectAThenBC, "Intersection should be associative")
        }
    }
    @Test fun `intersection is consistent with toSet intersect`() {
        genCharSetPair.foreach(100) { (charSetA, charSetB) ->
            val intersection = (charSetA intersect charSetB).toSet()
            val setIntersection = charSetA.toSet() intersect charSetB.toSet()
            assertEquals(setIntersection, intersection, "Intersection should be consistent with toSet intersect")
        }
    }
    @Test fun `consistency of containsAll with intersection`() {
        genCharSetTriple.foreach(100) { (a, b, c) ->
            // If a contains all elements of b and c,
            // then a should contain all elements of b intersect c.
            if (a.containsAll(b) && a.containsAll(c)) {
                assertTrue(a.containsAll(b intersect c))
            }
        }
    }
    @Test fun `times operator is implemented using intersect`() =
        genCharSetPair.foreach { (a, b) -> assertEquals(a intersect b, a * b) }

    // Inverse properties
    @Test fun `inverse is an involution`() =
        genCharSet.foreach(100) { assertEquals(it, it.invert().invert()) }
    @Test fun `union of CharSet and its inverse is CharSet all`() =
        genCharSet.foreach { assertEquals(CharSet.all, it.union(it.invert())) }
    @Test fun `intersection of CharSet and its inverse is CharSet none`() =
        genCharSet.foreach { assertEquals(CharSet.none, it.intersect(it.invert())) }
    @Test fun `inverse is consistent with contains`() {
        genCharSet.foreach(100) { charSet ->
            val inverseCharSet = charSet.invert()
            val allChars = (Char.MIN_VALUE..Char.MAX_VALUE).toList()
            allChars.all { it in inverseCharSet == (it !in charSet) }
        }
    }

    // Difference properties
    @Test fun `difference has default implementation in terms of inverse and intersect`() =
        genCharSetPair.foreach { (charSet1, charSet2) ->
            assertEquals(
                charSet1.intersect(charSet2.invert()),
                charSet1.difference(charSet2))
        }
    @Test fun `minus has default implementation in terms of difference`() =
        genCharSetPair.foreach { (charSet1, charSet2) ->
            assertEquals(charSet1.difference(charSet2), charSet1 - charSet2)
        }
    @Test fun `subtracting CharSet from itself yields empty set`() =
        genCharSet.foreach { assertEquals(CharSet.none, it - it) }
    @Test fun `subtracting empty set from any CharSet yields original CharSet`() =
        genCharSet.foreach { assertEquals(it, it - CharSet.none) }
    @Test fun `subtracting CharSet all from any CharSet yields empty set`() =
        genCharSet.foreach { assertEquals(CharSet.none, it - CharSet.all) }
    @Test fun `subtracting one CharSet from another is equivalent to intersecting with the inverse`() {
        genCharSetPair.foreach { (a, b) ->
            assertEquals(a intersect b.invert(), a - b)
        }
    }

    // Union/Intersection/Inverse properties
    @Test fun `distributive property of union over intersection`() {
        genCharSetTriple.foreach(100) { (a, b, c) ->
            val unionThenIntersect = (a union b) intersect c
            val intersectThenUnionA = (a intersect c) union (b intersect c)
            assertEquals(unionThenIntersect, intersectThenUnionA, "Distributive property of union over intersection failed")
        }
    }
    @Test fun `distributive property of intersection over union`() {
        genCharSetTriple.foreach(100) { (a, b, c) ->
            val intersectThenUnion = (a intersect b) union c
            val unionThenIntersectA = (a union c) intersect (b union c)
            assertEquals(intersectThenUnion, unionThenIntersectA, "Distributive property of intersection over union failed")
        }
    }
    @Test fun `union and intersection on subsets`() {
        genCharSetPair.foreach(100) { (a, b) ->
            // Create a union of the two sets to ensure a is a subset of the union
            val u = a union b

            // Test the union property: if a is a subset of b, then a union b should be equal to b
            assertEquals(u, a union u)
            assertEquals(u, u union a)

            // Test the intersection property: if a is a subset of b, then a intersect b should be equal to a
            assertEquals(a, a intersect u)
            assertEquals(a, u intersect a)
        }
    }
    @Test fun `other properties of union and intersection`() {
        genCharSetPair.foreach { (a, b) ->
            // Symmetric difference should be equivalent to union of differences
            assertEquals((a - b) + (b - a), (a + b) - (a intersect b))

            // De Morgan's Laws
            assertEquals((a + b).invert(), a.invert() intersect b.invert())
            assertEquals((a intersect b).invert(), a.invert() + b.invert())

            // absorption laws
            assertEquals(a, a union (a intersect b))
            assertEquals(a, a intersect (a union b))

            // Size of union should be the sum of the sizes minus the size of the intersection
            assertEquals(
                a.size + b.size - (a intersect b).size,
                (a union b).size
            )

            // Symmetric difference should be commutative
            assertEquals((a - b) + (b - a), (b - a) + (a - b))
        }
    }

    @Test fun `isSubsetOf is consistent with default implementation`() =
        genCharSetPair.foreach { (a, b) ->
            assertEquals((a intersect b) == a, a isSubsetOf b)
        }
    @Test fun `isSupersetOf is consistent with default implementation`() =
        genCharSetPair.foreach { (a, b) ->
            assertEquals((a intersect b) == b, a isSupersetOf b)
        }
    @Test fun `isDisjointWith is consistent with default implementation`() =
        genCharSetPair.foreach { (a, b) ->
            assertEquals((a intersect b).isEmpty(), a isDisjointWith b)
        }
    @Test fun `isOverlappingWith is consistent with default implementation`() =
        genCharSetPair.foreach { (a, b) ->
            assertEquals((a intersect b).isNotEmpty(), a isOverlappingWith b)
        }
    @Test fun `isProperSubsetOf is consistent with default implementation`() =
        genCharSetPair.foreach { (a, b) ->
            assertEquals((a intersect b) == a && a != b, a isProperSubsetOf b)
        }
    @Test fun `isProperSupersetOf is consistent with default implementation`() =
        genCharSetPair.foreach { (a, b) ->
            assertEquals((a intersect b) == b && a != b, a isProperSupersetOf b)
        }

    // toString properties
    @Test fun `equality on sets is equivalent to equality on toString`() =
        genCharSetPair.foreach { (a, b) ->
            assertEquals(a == b, a.toString() == b.toString()) }

    // Constructors
    @Test fun `constructor tests`() {
        assertEquals(true, 'v' in (CharSet.of("dmqstvw") intersect CharSet.of("bcijkmrtuvx")))
        assertEquals(true, 'a' in CharSet.of("abcefgijk"))
        assertEquals(true, 'k' in CharSet.of("abcefgijk"))
        assertEquals(false, 'l' in CharSet.of("abcefgijk"))
        assertEquals(false, 'h' in CharSet.of("abcefgijk"))
        assertEquals(false, 'a' in CharSet.of("bcefgijk"))
    }
}
