# Module kotlin-parsing-charset

Kotlin Multiplatform character-set algebra for parser and lexer implementations.

Use `CharSet` to represent a set of `Char` values as sorted, non-overlapping ranges. Use
`CharSetTop` and `Topology.charRanges` when multiple sets need to be refined into a partition of the
whole `Char` space.
