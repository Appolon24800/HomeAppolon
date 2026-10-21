# Sonarr queue and release contracts

This group covers all 26 operations under Queue, QueueAction, QueueDetails,
QueueStatus, Blocklist, History, ManualImport, Command, Release, ReleasePush,
Parse and Ping. `SonarrQueueApis` creates each interface. `FakeSonarrQueue`
exposes matching interface properties and mutable stores for test setup.

Dates remain strings. GET ping preserves HTTP status and its error body. HEAD
ping preserves status without attempting to deserialize a forbidden HEAD body.
Command lookup preserves the specified 404 response. Other failures throw
Retrofit exceptions for the connection layer's existing error mapping.

SeriesResource and EpisodeResource embeddings use JsonObject deliberately.
This group neither duplicates nor imports the library-owned series and episode
DTOs. Shared quality, language, custom format, alternate title and release type
models reuse the library's existing types. Every operation has a response
schema or explicitly returns no content. No missing-schema response needs a
JsonObject fallback.

The OpenAPI document has no examples for these operations. Fixtures therefore
use schema-generated values, enum members and synthetic ISO dates. They contain
no live-server data. Each operation has an HTTP happy-path test, and each tag
has an HTTP error test. Tests also check query wire values and path prefixes.

The fake applies filters, paging and common-field sorting to seeded state.
It records grab, push and reprocess actions. Latency and failure callbacks
receive `Tag.method` before each call. It does not contact download clients,
modify files, run commands or parse release titles. Seed parsedTitles to test
parsing results, and commandStore to test command transitions. Include flags
retain seeded embeddings rather than constructing library-owned payloads.

Run `python3 arr-api-sonarr/src/queue/generate_contract.py` at the repository
root to regenerate contracts, fixtures, HTTP tests and fake implementations.
The generator only writes inside this group's queue and release directories.
