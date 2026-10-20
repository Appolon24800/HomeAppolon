# Bazarr subtitle API group

All 37 operations in the 16 assigned tags are implemented here. Retrofit base URLs must
include the deployment prefix but exclude `api/`. A base of `https://example.test/bazarr/`
produces requests under `/bazarr/api/`. Authentication belongs to the connection layer.

The supplied spec is Swagger 2.0. None of these operations has a response schema or
response example, including subtitle contents and provider searches. Successful bodies
therefore use `JsonObject`, except documented bodyless 204 responses. The Plex webhook
returns `Response<JsonObject>` to distinguish processed 200 and ignored 204 events.
Fixtures use `{}` for unspecified JSON bodies. These are synthetic placeholders, not
invented descriptions of the server's actual response shape. Error fixtures likewise
use `{}` because only status descriptions are specified.

Request DTOs match all six referenced webhook definitions and the inline Plex server
selection body. `connections` is an optional string per the spec. Strings described as
boolean flags stay strings, and values listed only in descriptions do not become enums.
No date conversions occur. Any date strings in passthrough JSON remain unchanged.

The Plex PIN check route contains `{pin_id}` but has no parameter declaration. The
client supplies a required string path argument to make this route callable.

The module owner must add these source directories to the module's source sets:

* Main Kotlin sources: `src/subtitles/main/kotlin`
* Unit test Kotlin sources: `src/subtitles/test/kotlin`
* Unit test resources: `src/subtitles/test/resources`

Until then, isolated verification uses the owned `verification.init.gradle` script:

```sh
./gradlew :arr-api-bazarr:compileDebugKotlin --init-script arr-api-bazarr/src/subtitles/verification.init.gradle
./gradlew :arr-api-bazarr:testDebugUnitTest --init-script arr-api-bazarr/src/subtitles/verification.init.gradle
```

`FakeBazarrSubtitles` implements every interface. Its response and status maps use spec
operation IDs. Its call history retains arguments and request DTOs for mutation checks.
Inject a suspending `latency` callback for virtual-time tests and `errorInjection` for
transport failures. HTTP failures configured through `statusCodes` follow Retrofit:
JSON-returning methods throw `HttpException`, while response-returning methods expose
the status. This fake is intended for single-dispatcher UI tests.
