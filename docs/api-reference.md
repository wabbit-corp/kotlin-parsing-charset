# API Reference

Generate exact signatures locally with:

```bash
./gradlew dokkaGeneratePublicationHtml
```

## CharSet

`CharSet` is an immutable set of `Char` values backed by sorted, non-overlapping ranges.

Key members:

- `size`: number of characters in the set.
- `nonConsecutiveRangeCount`: number of stored ranges.
- `isEmpty`, `isNotEmpty`, `isAll`: shape checks.
- `contains`: single-character membership check.
- `containsAll(Collection<Char>)`: collection membership check.
- `containsAll(CharSet)`: set containment check.
- `sample`: choose a random contained character.
- `overlap`: classify overlap with another set.
- `map`, `filter`, `count`: per-character transformations and queries.
- `invert`, `union`, `intersect`, `difference`: set algebra.
- subset, superset, disjoint, overlap, and proper subset/superset predicates.
- `toSet`, `toList`, `toRangeList`, `toCharArray`: materialization helpers.
- `iterator`, `forEach`: iteration helpers.

Factory and constant members:

- `none`
- `all`
- `one`
- `range`
- `of`
- `union`
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

The `of(vararg CharRange)` overload rejects empty ranges, including Kotlin ranges whose `first` is
greater than `last`.

## CharSetTop

`CharSetTop` represents a partition of the complete `Char` space.

Key members:

- `basis`: list of adjacent `CharRange` partitions.
- `size`: number of partitions.
- `refine(CharSetTop)`: combine two partitions.
- `refine(CharSet)`: refine a partition with a character set.
- `trivial`: one-range partition covering every `Char`.
- `fromSet`: construct a partition from a `CharSet`.

## Topology

`SetLike`, `SetLike1`, and `Topology` define generic set operations used by parser infrastructure.

`Topology.charRanges` is the provided topology instance for `Char`, `CharSet`, and `CharSetTop`.

## Overlap

`Overlap` classifies two sets as:

- `EMPTY`
- `PARTIAL`
- `FIRST_CONTAINS_SECOND`
- `SECOND_CONTAINS_FIRST`
- `EQUAL`
