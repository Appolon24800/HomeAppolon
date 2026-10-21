# Bazarr library API

This source tree owns the 14 library tags and their 26 operations in the bundled Bazarr
Swagger 2.0 spec. Production Kotlin lives in `kotlin/`. Tests and resources live in
`src/test/kotlin/` and `src/test/resources/` to keep all changes inside the assigned tree.
The module owner must include those directories in the corresponding Android source sets.
Until then, pass `-I arr-api-bazarr/src/library/verify.gradle` to the module compile and
unit-test Gradle commands to verify these sources without editing the module build file.

The spec defines only RadarrWebhook and SonarrWebhook payloads. It has no series,
episodes, movies, history, wanted, blacklist, tag or badge response definitions, and no
response schemas or examples anywhere. Every successful JSON response in these tags
therefore uses JsonObject passthrough. There are no invented DTOs, date conversions or
closed action enums. Query strings retain the server's wire values, including string
boolean flags and repeated arrays. Optional queries default to omission so the server
applies its documented defaults. Multipart callers must name the uploaded part `file`.

Fixtures are minimal synthetic JSON objects because the spec has no examples or
constraints for these responses. They are not recordings of a live server and do not
claim to document an unknown server response structure.

The fake implements every interface, records all calls, and returns seeded response
objects keyed by Swagger operation ID. It exposes suspending latency, injected HTTP
status or throwable failures, and a successful-write mutation callback for test-specific
state transitions. It deliberately does not guess undocumented server payload fields.
