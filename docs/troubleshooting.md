# Troubleshooting

## `CharSet.range` Throws

`CharSet.range(from, to)` requires `from <= to`.

```kotlin
CharSet.range('z', 'a') // throws
```

Use `CharSet.none` for an empty set.

## Empty `CharRange` Values Are Not Ignored

`CharSet.of(vararg range: CharRange)` also requires every supplied range to be non-empty. Kotlin can
represent an empty `CharRange` such as `'z'..'a'`; this library rejects that input instead of
silently dropping it.

## Large Materializations Are Expensive

`toSet`, `toList`, and `toCharArray` materialize every contained character. Calling them on
`CharSet.all` or broad Unicode sets can allocate large collections.

Prefer range-based operations such as `contains`, `union`, `intersect`, `difference`, and
`toRangeList` when possible.

## `sample` Throws On Empty Sets

`sample(random)` delegates to indexed lookup and requires at least one contained character. Check
`isNotEmpty()` before sampling if the set may be empty.

## `CharSetTop` Constructor Checks Fail

`CharSetTop` requires its `basis` ranges to:

- be non-empty
- be individually non-empty
- be adjacent with no gaps
- start at `Char.MIN_VALUE`
- end at `Char.MAX_VALUE`

Use `CharSetTop.trivial`, `CharSetTop.fromSet`, or `refine` instead of constructing custom basis
lists by hand.

## Topology Basis Size Grows

Every refinement can add cut points. This is expected: a refined topology has enough partitions to
distinguish all introduced character-set boundaries.
