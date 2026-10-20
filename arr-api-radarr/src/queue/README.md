# Radarr queue API contracts

This group owns all 21 operations tagged Queue, QueueAction, QueueDetails,
QueueStatus, Blocklist, History, ManualImport, and Command in the checked-in spec.
`generate_queue.py` regenerates the DTOs, Retrofit contracts, factory, fixtures,
and HTTP contract tests. It does not generate the fake or fake tests.

The spec provides no operation examples for these tags. Fixtures use schema
types, enum values, nullable fields, and synthetic date strings instead of live
server data. Paging fixtures contain one schema-derived resource.

The following responses have empty schemas and use `JsonObject` passthrough:

- DELETE `api/v3/blocklist/bulk`
- POST `api/v3/history/failed/{id}`
- DELETE `api/v3/queue/bulk`
- POST `api/v3/queue/grab/{id}`
- POST `api/v3/queue/grab/bulk`

Dates and durations remain strings. DTO defaults let partial responses decode;
nullable properties follow the spec. Enumerated values have wire annotations,
wire-valued `toString` for Retrofit queries, and defaults for ArrJson's unknown
enum coercion. Resource properties with no enum stay strings.

`FakeRadarrQueue` implements all eight interfaces with mutable seed collections.
Its delay and per-operation failure hooks run before mutations. Inject coroutine
delay when testing coroutine scheduling. The fake records download-client flags
and grab requests rather than contacting download clients. It does not run movie
analysis, validate command names, or perform filesystem imports. Embedded movies
remain present in seeded resources regardless of `includeMovie`, because the
spec does not allow a null movie reference.

The History list endpoint accepts integer event filters, but its event schema
only lists strings. Configure the fake's `historyEventIds` map when testing that
filter. No integer mapping is guessed from enum order.
