# Sonarr library API group

This group implements all 24 operations in the Series, SeriesEditor, SeriesFolder,
SeriesImport, SeasonPass, Episode, EpisodeFile, RenameEpisode and MediaCover tags.
The DTOs include every property in the reachable OpenAPI schemas. Dates remain strings.
Enums exist only for schemas with an explicit enum list. ArrJson coerces unknown enum
values to the DTO field's default. Unconstrained strings remain strings.

`SonarrLibraryApis` creates the nine Retrofit interfaces. `FakeSonarrLibrary` exposes
the same nine interfaces as properties, with shared mutable stores. Its suspend
latency callback and error callback receive a name such as `Series.list`.
The fake does not modify the file system or generate actual cover images.

## Missing response schemas

`GET /api/v3/series/{id}/folder` and
`GET /api/v3/mediacover/{seriesId}/{filename}` declare no response schema or media
type. Both return JsonObject passthrough as required by the implementation brief.
If MediaCover returns binary image bytes, a separately agreed binary contract is
needed before using this method to display images.
Mutations with no documented response content return Unit.
Episode and EpisodeFile GET-by-ID preserve Response so callers can distinguish 404.

The spec has no examples for these operations. Fixtures contain synthetic values
derived from its schemas. No fixture contains live-server data.

## Source set registration

All group files remain under src/library, the assigned ownership boundary.
The orchestrator must add src/library/kotlin to variant Kotlin sources,
src/library/test/kotlin to unit-test Kotlin sources, and src/library/test/resources
to unit-test resources. AGP 9 supports this through androidComponents onVariants
and each variant's sources.kotlin.addStaticSourceDirectory.

Verification uses an external Gradle init script to register these directories
without editing the shared build file. Both compileDebugKotlin and testDebugUnitTest
pass with that registration. Ordinary module tasks do not include this group until
the orchestrator registers its source sets.
