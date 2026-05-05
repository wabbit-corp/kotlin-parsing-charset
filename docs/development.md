# Development

`kotlin-parsing-charset` is a Kotlin Multiplatform library. In the monorepo workspace, build and test
it through the `dev` tooling:

```bash
./dev build kotlin-parsing-charset
```

From the project directory, or from a standalone checkout, run Gradle directly:

```bash
./gradlew build
```

## Documentation Checks

Run repository documentation checks with:

```bash
./dev verify docs kotlin-parsing-charset
```

Build generated API docs with:

```bash
cd kotlin-parsing-charset
./gradlew dokkaGeneratePublicationHtml
```

## Publication Dry Run

Before release publication, run:

```bash
./dev publish --dry-run kotlin-parsing-charset
```

## Documentation Standards

Public KDoc should state:

- whether a helper operates on characters, ranges, sets, or top-level partitions
- whether operations preserve immutability
- when methods materialize every character
- constructor and factory preconditions
- the meaning of `Overlap` and topology refinement results
