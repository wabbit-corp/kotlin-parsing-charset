package one.wabbit.parsing.charset

import org.junit.Test
import one.wabbit.random.gen.Gen
import one.wabbit.random.gen.foreach
import java.util.*
import kotlin.test.assertEquals

class CharSetTopSpec {
    @Test
    fun testCharSetTop() {
        val genString = Gen.int(0 ..< 20).flatMap { Gen.repeat(it, Gen.int('a'.code .. 'z'.code).map { it.toChar() }) }
            .map { it.joinToString("") }

        var top = CharSetTop.trivial
        top = top.refine(CharSet.of("bc"))
        assertEquals(CharSetTop(listOf(Char.MIN_VALUE..'a', 'b'.. 'c', 'd'..Char.MAX_VALUE)), top)
        top = top.refine(CharSet.of("c"))
        assertEquals(CharSetTop(listOf(Char.MIN_VALUE..'a', 'b'..'b', 'c'..'c', 'd'..Char.MAX_VALUE)), top)

        val random = SplittableRandom()
        genString.foreach(random, 100) { str1 ->
            top = top.refine(CharSet.of(str1))
            assertEquals(top, top.refine(CharSet.of(str1)))
            assertEquals(top, top.refine(CharSet.of(str1).invert()))
            assertEquals(top, top.refine(top))
        }

        for (c in 'a'..'z') {
            top = top.refine(CharSet.one(c))
        }

        assertEquals(Char.MIN_VALUE, top.basis[0].first)
        assertEquals('a' - 1, top.basis[0].last)
        assertEquals('z' + 1, top.basis.last().first)
        assertEquals(Char.MAX_VALUE, top.basis.last().last)
    }
}
