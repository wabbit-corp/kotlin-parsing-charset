# User Guide

`kotlin-parsing-charset` provides a compact `CharSet` representation for parser and lexer code.

A `CharSet` is immutable and stores sorted, non-overlapping character ranges. This keeps contiguous
sets such as `a..z` compact while still supporting arbitrary sparse sets.

## Creating Sets

```kotlin
import one.wabbit.parsing.charset.CharSet

val empty = CharSet.none
val allChars = CharSet.all
val single = CharSet.one('x')
val range = CharSet.range('a', 'z')
val fromString = CharSet.of("abcxyz")
```

Factories normalize their input by sorting characters and merging adjacent runs.

## Membership

```kotlin
val digit = CharSet.digit

check('5' in digit)
check('x' !in digit)
```

Small sets use a linear range scan. Larger sets use a binary search over range endpoints.

## Set Algebra

```kotlin
val letterOrUnderscore = CharSet.letter union CharSet.one('_')
val identifierPart = letterOrUnderscore union CharSet.digit
val onlyLetters = identifierPart difference CharSet.digit

check(onlyLetters.containsAll(CharSet.letter))
check(identifierPart isSupersetOf letterOrUnderscore)
```

Available operations include:

- `union`
- `intersect`
- `difference`
- `invert`
- `isSubsetOf`
- `isSupersetOf`
- `isDisjointWith`
- `isOverlappingWith`
- `isProperSubsetOf`
- `isProperSupersetOf`

Operator aliases are also available:

- `!set` for inversion
- `a + b` for union
- `a - b` for difference
- `a * b` for intersection

## Common Constants

`CharSet` includes common ready-made sets:

- `ascii`
- `digit`
- `letter`
- `letterOrDigit`
- `hexDigit`
- `whitespace`
- `validUnicode`
- `unicodeDigit`
- `unicodeLetter`
- `unicodeLetterOrDigit`
- `unicodeWhitespace`

The `unicode*` constants are based on Kotlin `Char` classification functions.

## Top-Level Partitions

`CharSetTop` partitions the complete `Char` domain into adjacent ranges. Refining a partition with a
set inserts that set's boundaries into the partition.

```kotlin
import one.wabbit.parsing.charset.CharSet
import one.wabbit.parsing.charset.CharSetTop

val top = CharSetTop.trivial
    .refine(CharSet.digit)
    .refine(CharSet.letter)

val basis = top.basis
```

This is useful when constructing deterministic parser or lexer tables where every input character
must fall into exactly one basis range.

## Generic Topology API

`SetLike`, `SetLike1`, and `Topology` provide a generic algebra over set implementations. The
provided `Topology.charRanges` instance adapts `CharSet` and `CharSetTop`.
