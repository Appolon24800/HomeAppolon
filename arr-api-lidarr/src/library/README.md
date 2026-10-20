# Lidarr library clients

This source group owns 28 operations across Artist, ArtistEditor, ArtistLookup,
Album, AlbumLookup, AlbumStudio, Track, TrackFile, RenameTrack, RetagTrack and MediaCover.

The module registers `src/library/kotlin` as a main Kotlin source directory,
`src/library/src/test/kotlin` as a test Kotlin source directory, and
`src/library/src/test/resources` as a test resource directory.

```sh
./gradlew :arr-api-lidarr:compileDebugKotlin
./gradlew :arr-api-lidarr:testDebugUnitTest
```

`generate_library.py` derives contracts and fixtures from the checked-in specification.
The spec contains no examples for these response schemas. Fixtures therefore use
schema-shaped synthetic values, never server data. The generator rejects uncovered
operations under the assigned tags. It does not overwrite the handwritten fake.

Dates and durations remain strings. Nullable fields retain null. Optional scalar fields
use wire-type defaults. Optional object references default to null to allow omitted
fields and prevent recursive ArtistResource and AlbumResource defaults.
Enums exist only for enumerations explicitly present in the spec. ArrJson coerces
unknown enum values to the property's default.

## Missing response schemas

The following responses use JsonObject passthrough because no schema is supplied.

* DELETE api/v1/album/{id}
* PUT api/v1/album/monitor
* POST api/v1/albumstudio
* DELETE api/v1/artist/{id}
* PUT and DELETE api/v1/artist/editor
* GET api/v1/mediacover/artist/{artistId}/{filename}
* GET api/v1/mediacover/album/{albumId}/{filename}
* DELETE api/v1/trackfile/{id}
* PUT api/v1/trackfile/editor
* DELETE api/v1/trackfile/bulk

MediaCover endpoints likely return binary image bodies on a real server. The spec
does not describe this. JsonObject follows the task's required missing-schema policy,
but callers must not treat it as an image downloader. Empty mutation responses also
cannot decode as JSON. Resolve these spec gaps before connecting those operations to
a real server.

The fake stores JSON cover stand-ins and applies library mutations in memory. It does
not move files on disk, contact metadata providers, discover additional albums, or
calculate monitoring modes that require release dates or download history.
Set its suspend latency callback to a coroutine delay for cancellable simulated delay.
Set its error callback to return an exception for any API method name. Each callback
runs before mutation, so injected failures leave state unchanged.
