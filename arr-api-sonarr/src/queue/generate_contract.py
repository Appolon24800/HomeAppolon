"""Regenerate the owned Sonarr contracts and schema-derived HTTP tests.

Run from the repository root. Library series and episode embeddings stay opaque.
No server data or credentials are used.
"""
import json
import re
from pathlib import Path

ROOT = Path('arr-api-sonarr/src')
SPEC = json.loads(Path('docs/arr-api/sonarr.openapi.json').read_text())
SCHEMAS = SPEC['components']['schemas']
PKG = 'dev.appolon.homelab.arr.api.sonarr'
TAGS = ['Queue', 'QueueAction', 'QueueDetails', 'QueueStatus', 'Blocklist',
        'History', 'ManualImport', 'Command', 'Release', 'ReleasePush', 'Parse', 'Ping']
OPAQUE = {'SeriesResource', 'EpisodeResource'}
EXISTING = {p.stem for p in (ROOT / 'library/kotlin').rglob('*.kt')}
OWNED = set()


def group(tag):
    return 'release' if tag in {'Release', 'ReleasePush', 'Parse'} else 'queue'


def write(g, path, text):
    if path.endswith('.kt'):
        lines = text.splitlines()
        body = '\n'.join(line for line in lines if not line.startswith('import '))
        text = '\n'.join(
            line for line in lines
            if not line.startswith('import ') or
            re.search(r'\b' + re.escape(line.rsplit('.', 1)[-1]) + r'\b', body)
        ) + '\n'
    p = ROOT / g / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)


def enum_constant(value):
    return re.sub(r'(?<!^)(?=[A-Z])', '_', value).upper()


def typename(s):
    if '$ref' in s:
        n = s['$ref'].split('/')[-1]
        t = 'JsonObject' if n in OPAQUE else n
    elif s.get('type') == 'array':
        t = 'List<' + typename(s['items']) + '>'
    elif s.get('type') == 'object':
        a = s.get('additionalProperties')
        t = 'Map<String, ' + typename(a) + '>' if isinstance(a, dict) else 'JsonObject'
    else:
        t = {'integer': 'Long' if s.get('format') == 'int64' else 'Int',
             'number': 'Double', 'boolean': 'Boolean', 'string': 'String'}.get(
                 s.get('type'), 'JsonObject')
    return t + ('?' if s.get('nullable') else '')


def default(s):
    if s.get('nullable'):
        return 'null'
    if '$ref' in s:
        n = s['$ref'].split('/')[-1]
        if n in OPAQUE:
            return 'JsonObject(emptyMap())'
        if 'enum' in SCHEMAS[n]:
            return n + '.' + enum_constant(SCHEMAS[n]['enum'][0])
        return n + '()'
    if s.get('format') == 'date-time':
        return '"1970-01-01T00:00:00Z"'
    return {'String': '""', 'Int': '0', 'Long': '0L', 'Double': '0.0',
            'Boolean': 'false', 'JsonObject': 'JsonObject(emptyMap())'}.get(
                typename(s), 'emptyList()' if s.get('type') == 'array' else 'emptyMap()')


def model(n, g):
    if n in OWNED or n in EXISTING or n in OPAQUE:
        return
    OWNED.add(n)
    s = SCHEMAS[n]
    imports = 'import kotlinx.serialization.Serializable\n'
    if 'enum' in s:
        imports += 'import kotlinx.serialization.SerialName\n'
        members = ',\n'.join('    @SerialName(' + json.dumps(v) + ')\n    ' +
                             enum_constant(v) + '(' + json.dumps(v) + ')'
                             for v in s['enum'])
        body = ('/** Wire values defined by the Sonarr ' + n + ' schema. */\n'
                '@Serializable\nenum class ' + n + '(val wireValue: String) {\n' +
                members + ';\n\n    override fun toString(): String = wireValue\n}\n')
    else:
        fields = []
        for k, v in s.get('properties', {}).items():
            fields.append('    val ' + k + ': ' + typename(v) + ' = ' + default(v))
        if any('JsonObject' in f for f in fields):
            imports += 'import kotlinx.serialization.json.JsonObject\n'
        body = ('/** Sonarr ' + n + ' wire contract. Dates remain unparsed strings. */\n'
                '@Serializable\ndata class ' + n + '(\n' + ',\n'.join(fields) + '\n)\n')
    write(g, 'kotlin/' + PKG.replace('.', '/') + '/model/' + n + '.kt',
          'package ' + PKG + '.model\n\n' + imports + '\n' + body)
    def walk(x):
        if isinstance(x, dict):
            if '$ref' in x:
                model(x['$ref'].split('/')[-1], g)
            else:
                for v in x.values():
                    walk(v)
        elif isinstance(x, list):
            for v in x:
                walk(v)
    walk(s)


def fixture(s):
    if 'example' in s:
        return s['example']
    if '$ref' in s:
        n = s['$ref'].split('/')[-1]
        return {} if n in OPAQUE else fixture(SCHEMAS[n])
    if 'enum' in s:
        return s['enum'][0]
    if s.get('nullable'):
        return None
    t = s.get('type')
    if t == 'object':
        return {k: fixture(v) for k, v in s.get('properties', {}).items()}
    if t == 'array':
        return [fixture(s['items'])]
    if s.get('format') == 'date-time':
        return '2026-01-01T00:00:00Z'
    return {'integer': 1, 'number': 1.0, 'boolean': False, 'string': 'string'}.get(t, {})


OPERATIONS = {}
for tag in TAGS:
    ops = []
    for path, methods in SPEC['paths'].items():
        for verb, op in methods.items():
            if tag not in op.get('tags', []):
                continue
            parts = path.split('/')
            name = {'get': 'list', 'post': 'create', 'delete': 'delete', 'head': 'head'}[verb]
            if '{id}' in path and verb == 'get':
                name = 'get'
            if 'bulk' in parts:
                name += 'Bulk'
            if 'since' in parts:
                name = 'since'
            if 'series' in parts:
                name = 'series'
            if 'failed' in parts:
                name = 'markFailed'
            params = []
            for p in op.get('parameters', []):
                s = p['schema']
                if '$ref' in s:
                    model(s['$ref'].split('/')[-1], group(tag))
                if s.get('type') == 'array' and '$ref' in s['items']:
                    model(s['items']['$ref'].split('/')[-1], group(tag))
                t = typename(s)
                if not p.get('required') and not t.endswith('?'):
                    t += '?'
                params.append((p['name'], t, '@' + p['in'].title() + '(' +
                               json.dumps(p['name']) + ')', s))
            body = op.get('requestBody', {}).get('content', {}).get('application/json', {}).get('schema')
            if body:
                params.append(('body', typename(body), '@Body', body))
            content = op['responses']['200'].get('content', {})
            response = next(iter(content.values()))['schema'] if content else None
            result = typename(response) if response else 'Unit'
            if tag == 'Ping':
                result = 'Response<Unit>' if verb == 'head' else 'Response<PingResource>'
            elif tag == 'Command' and name == 'get':
                result = 'Response<CommandResource>'
            def refs(s):
                if not s:
                    return
                if '$ref' in s:
                    model(s['$ref'].split('/')[-1], group(tag))
                if 'items' in s:
                    refs(s['items'])
            refs(body)
            refs(response)
            ops.append(dict(tag=tag, name=name, verb=verb, path=path, params=params,
                            result=result, response=response, body=body))
    OPERATIONS[tag] = ops
    imports = {'import retrofit2.http.' + v.upper() for v in {o['verb'] for o in ops}}
    for o in ops:
        for _, t, annotation, _ in o['params']:
            imports.add('import retrofit2.http.' + annotation[1:].split('(')[0])
        for s in ([p[3] for p in o['params']] + [o['response']]):
            def collect(s):
                if not s:
                    return
                if '$ref' in s:
                    imports.add('import ' + PKG + '.model.' + s['$ref'].split('/')[-1])
                if 'items' in s:
                    collect(s['items'])
            collect(s)
        if o['result'].startswith('Response'):
            imports.add('import retrofit2.Response')
    # Retrofit requires @HTTP for DELETE requests carrying a body.
    lines = []
    for o in ops:
        if o['verb'] == 'delete' and o['body']:
            imports.add('import retrofit2.http.HTTP')
            annotation = '@HTTP(method = "DELETE", path = "' + o['path'].lstrip('/') + '", hasBody = true)'
        else:
            annotation = '@' + o['verb'].upper() + '("' + o['path'].lstrip('/') + '")'
        args = ',\n'.join('        ' + a + ' ' + n + ': ' + t +
                          (' = null' if t.endswith('?') else '') for n, t, a, _ in o['params'])
        lines.append('    ' + annotation + '\n    suspend fun ' + o['name'] + '(' +
                     ('\n' + args + '\n    ' if args else '') + '): ' + o['result'])
    write(group(tag), 'kotlin/' + PKG.replace('.', '/') + '/' + tag + 'Api.kt',
          'package ' + PKG + '\n\n' + '\n'.join(sorted(imports)) + '\n\n' +
          '/** Operations in the Sonarr ' + tag + ' OpenAPI tag. */\n@JvmSuppressWildcards\ninterface ' + tag +
          'Api {\n' + '\n\n'.join(lines) + '\n}\n')

factory = '\n'.join('    fun ' + t[0].lower() + t[1:] + '(retrofit: Retrofit): ' +
                    t + 'Api =\n        retrofit.create(' + t + 'Api::class.java)' for t in TAGS)
write('queue', 'kotlin/' + PKG.replace('.', '/') + '/SonarrQueueApis.kt',
      'package ' + PKG + '\n\nimport retrofit2.Retrofit\n\n' +
      '/** Queue, import, history, command, release, parsing and ping API factories. */\n' +
      'object SonarrQueueApis {\n' + factory + '\n}\n')

# Every endpoint gets its own HTTP test, including the two body-carrying DELETEs.
for tag, ops in OPERATIONS.items():
    imports = {'import ' + PKG + '.model.' + n for n in OWNED}
    tests = []
    for o in ops:
        fn = o['name']
        data = fixture(o['response']) if o['response'] else None
        resource = 'fixtures/sonarr/' + tag.lower() + '/' + fn + '.json'
        write(group(tag), 'src/test/resources/' + resource,
              json.dumps(data, indent=2) + '\n')
        args = []
        queries = []
        for n, t, a, s in o['params']:
            if n == 'body':
                v = 'listOf(ManualImportReprocessResource())' if s.get('type') == 'array' else default(s)
            else:
                ss = dict(s)
                ss.pop('nullable', None)
                v = default(ss)
                if s.get('type') == 'integer':
                    v = '7'
                elif s.get('type') == 'string':
                    v = '"sample"'
                elif s.get('type') == 'array':
                    v = 'listOf(' + ('7' if s['items'].get('type') == 'integer' else default(s['items'])) + ')'
                if a.startswith('@Query'):
                    expected = '7' if s.get('type') in {'integer', 'array'} else 'sample' if s.get('type') == 'string' else 'false'
                    if '$ref' in s:
                        expected = SCHEMAS[s['$ref'].split('/')[-1]]['enum'][0]
                    if s.get('type') == 'array' and '$ref' in s['items']:
                        expected = SCHEMAS[s['items']['$ref'].split('/')[-1]]['enum'][0]
                    queries.append('        assertEquals(' + json.dumps(expected) + ', request.requestUrl!!.queryParameter("' + n + '"))')
            args.append(n + ' = ' + v)
        path = o['path'].replace('{id}', '7')
        if o['result'].startswith('Response'):
            if o['verb'] == 'head':
                invocation = 'assertEquals(200, api.FN(ARGS).code())'
            else:
                rt = o['result'][len('Response<'):-1]
                invocation = ('val response = api.FN(ARGS)\n        assertEquals(200, response.code())\n' +
                              '        assertEquals(ArrJson.decodeFromString<' + rt +
                              '>(fixture("RESOURCE")), response.body())')
        elif o['response']:
            invocation = ('assertEquals(ArrJson.decodeFromString<' + o['result'] +
                          '>(fixture("RESOURCE")), api.FN(ARGS))')
        else:
            invocation = 'assertEquals(Unit, api.FN(ARGS))'
        # Split setup from assertions so long generic type names remain readable.
        if o['response'] and o['verb'] != 'head':
            rt = o['result'][len('Response<'):-1] if o['result'].startswith('Response') else o['result']
            invocation = ('val expected = ArrJson.decodeFromString<' + rt + '>(\n' +
                          '            fixture("RESOURCE")\n        )\n' +
                          '        val actual = api.FN(ARGS)\n' +
                          ('        assertEquals(200, actual.code())\n        assertEquals(expected, actual.body())'
                           if o['result'].startswith('Response') else
                           '        assertEquals(expected, actual)'))
        args_text = ', '.join(args)
        if len(args_text) > 50:
            args_text = '\n' + ',\n'.join('            ' + a for a in args) + '\n        '
        tests.append('''    @Test
    fun FNHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("RESOURCE"))
        )
        INVOCATION
        val request = server.takeRequest()
        assertEquals("VERB", request.method)
        assertEquals("/sonarrPATH", request.requestUrl!!.encodedPath)
QUERIES
BODY_CHECK
    }
'''.replace('INVOCATION', invocation).replace('FN', fn).replace('RESOURCE', resource).replace('ARGS', args_text)
                     .replace('VERB', o['verb'].upper()).replace('PATH', path)
                     .replace('QUERIES', '\n'.join(queries))
                     .replace('BODY_CHECK', '        assertTrue(request.body.size > 0)' if o['body'] else ''))
    o = ops[0]
    args = ',\n'.join('                ' + n + ' = ' + ('null' if t.endswith('?') else
                    'emptyList()' if s.get('type') == 'array' else default(s))
                    for n, t, _, s in o['params'])
    call = 'api.' + o['name'] + '(' + ('\n' + args + '\n            ' if args else '') + ')'
    err = ('        assertEquals(503, ' + call + '.code())' if o['result'].startswith('Response') else
           '        try {\n            ' + call + '\n            fail("Expected HTTP failure")\n' +
           '        } catch (error: HttpException) {\n            assertEquals(503, error.code())\n        }')
    tests.append('    @Test\n    fun httpError() = runTest {\n' +
                 '        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))\n' + err + '\n    }\n')
    imports.update(['import dev.appolon.homelab.arr.net.ArrJson',
                    'import kotlinx.serialization.decodeFromString',
                    'import kotlinx.coroutines.test.runTest', 'import okhttp3.MediaType.Companion.toMediaType',
                    'import okhttp3.mockwebserver.MockResponse', 'import okhttp3.mockwebserver.MockWebServer',
                    'import org.junit.After', 'import org.junit.Before', 'import org.junit.Test',
                    'import org.junit.Assert.assertEquals', 'import org.junit.Assert.assertTrue',
                    'import org.junit.Assert.fail', 'import retrofit2.HttpException',
                    'import retrofit2.Retrofit',
                    'import retrofit2.converter.kotlinx.serialization.asConverterFactory'])
    text = ('package ' + PKG + '\n\n' + '\n'.join(sorted(imports)) + '\n\nclass Sonarr' + tag +
            'ContractTest {\n    private val server = MockWebServer()\n    private lateinit var api: ' + tag + 'Api\n\n' +
            '    @Before\n    fun setUp() {\n        server.start()\n' +
            '        api = SonarrQueueApis.' + tag[0].lower() + tag[1:] + '(\n' +
            '            Retrofit.Builder().baseUrl(server.url("/sonarr/"))\n' +
            '                .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))\n' +
            '                .build()\n        )\n    }\n\n' +
            '    @After\n    fun tearDown() {\n        server.shutdown()\n    }\n\n' +
            '    private fun fixture(path: String): String =\n' +
            '        checkNotNull(javaClass.classLoader!!.getResource(path)).readText()\n\n' +
            '\n'.join(tests) + '}\n')
    write(group(tag), 'src/test/kotlin/' + PKG.replace('.', '/') + '/Sonarr' + tag + 'ContractTest.kt', text)

# Signature metadata is useful for reviewing fake coverage against the source spec.
write('queue', 'contract_operations.json', json.dumps(OPERATIONS, indent=2) + '\n')
print('Generated', sum(map(len, OPERATIONS.values())), 'operations and', len(OWNED), 'models')

FAKE_BODIES = {
    ('Queue', 'delete'): 'queueStore.remove(id)\nreturn Unit',
    ('Queue', 'deleteBulk'): 'body.ids.orEmpty().forEach(queueStore::remove)\nreturn Unit',
    ('Queue', 'list'): '''val rows = queueStore.values.filter {
    (seriesIds == null || it.seriesId in seriesIds) &&
        (protocol == null || it.protocol == protocol) &&
        (status == null || it.status in status) &&
        (languages == null || it.languages.orEmpty().any { language ->
            language.id in languages
        }) &&
        (quality == null || it.quality.quality.id in quality) &&
        (includeUnknownSeriesItems == true || it.seriesId != null)
}
val sorted = sorted(rows, sortKey, sortDirection)
return QueueResourcePagingResource(
    page = page ?: 1, pageSize = pageSize ?: 10, sortKey = sortKey,
    sortDirection = sortDirection ?: SortDirection.DEFAULT,
    totalRecords = rows.size, records = paged(sorted, page, pageSize)
)''',
    ('QueueAction', 'create'): '''val row = queueStore[id] ?: missing(id)
queueStore[id] = row.copy(status = QueueStatus.DOWNLOADING)
grabbedQueueIds.add(id)
return Unit''',
    ('QueueAction', 'createBulk'): '''body.ids.orEmpty().forEach { id ->
    val row = queueStore[id] ?: missing(id)
    queueStore[id] = row.copy(status = QueueStatus.DOWNLOADING)
    grabbedQueueIds.add(id)
}
return Unit''',
    ('QueueDetails', 'list'): '''return queueStore.values.filter {
    (seriesId == null || it.seriesId == seriesId) &&
        (episodeIds == null || it.episodeId in episodeIds)
}''',
    ('QueueStatus', 'list'): '''val unknown = queueStore.values.filter { it.seriesId == null }
return QueueStatusResource(
    totalCount = queueStore.size, count = queueStore.size - unknown.size,
    unknownCount = unknown.size,
    errors = queueStore.values.any {
        it.trackedDownloadStatus == TrackedDownloadStatus.ERROR
    },
    warnings = queueStore.values.any {
        it.trackedDownloadStatus == TrackedDownloadStatus.WARNING
    },
    unknownErrors = unknown.any {
        it.trackedDownloadStatus == TrackedDownloadStatus.ERROR
    },
    unknownWarnings = unknown.any {
        it.trackedDownloadStatus == TrackedDownloadStatus.WARNING
    }
)''',
    ('Blocklist', 'list'): '''val rows = blocklistStore.values.filter {
    (seriesIds == null || it.seriesId in seriesIds) &&
        (protocols == null || it.protocol in protocols)
}
return BlocklistResourcePagingResource(
    page = page ?: 1, pageSize = pageSize ?: 10, sortKey = sortKey,
    sortDirection = sortDirection ?: SortDirection.DEFAULT,
    totalRecords = rows.size,
    records = paged(sorted(rows, sortKey, sortDirection), page, pageSize)
)''',
    ('Blocklist', 'delete'): 'blocklistStore.remove(id)\nreturn Unit',
    ('Blocklist', 'deleteBulk'): 'body.ids.orEmpty().forEach(blocklistStore::remove)\nreturn Unit',
    ('History', 'list'): '''val rows = historyStore.values.filter {
    (episodeId == null || it.episodeId == episodeId) &&
        (downloadId == null || it.downloadId == downloadId) &&
        (seriesIds == null || it.seriesId in seriesIds) &&
        (eventType == null || it.eventType.ordinal in eventType) &&
        (languages == null || it.languages.orEmpty().any { language ->
            language.id in languages
        }) &&
        (quality == null || it.quality.quality.id in quality)
}
return HistoryResourcePagingResource(
    page = page ?: 1, pageSize = pageSize ?: 10, sortKey = sortKey,
    sortDirection = sortDirection ?: SortDirection.DEFAULT,
    totalRecords = rows.size,
    records = paged(sorted(rows, sortKey, sortDirection), page, pageSize)
)''',
    ('History', 'since'): '''return historyStore.values.filter {
    (date == null || Instant.parse(it.date) >= Instant.parse(date)) &&
        (eventType == null || it.eventType == eventType)
}''',
    ('History', 'series'): '''return historyStore.values.filter {
    (seriesId == null || it.seriesId == seriesId) &&
        (eventType == null || it.eventType == eventType) &&
        (seasonNumber == null ||
            it.episode["seasonNumber"]?.toString() == seasonNumber.toString())
}''',
    ('History', 'markFailed'): '''val row = historyStore[id] ?: missing(id)
historyStore[id] = row.copy(eventType = EpisodeHistoryEventType.DOWNLOAD_FAILED)
return Unit''',
    ('ManualImport', 'list'): '''return manualImports.filter {
    (downloadId == null || it.downloadId == downloadId) &&
        (folder == null || it.path?.startsWith(folder) == true) &&
        (seriesId == null || it.series?.get("id")?.toString() == seriesId.toString()) &&
        (seasonNumber == null || it.seasonNumber == seasonNumber) &&
        (filterExistingFiles == false ||
            it.episodeFileId == null || it.episodeFileId == 0)
}''',
    ('ManualImport', 'create'): '''reprocessedImports.addAll(body)
body.forEach { update ->
    val index = manualImports.indexOfFirst { it.id == update.id }
    if (index >= 0) {
        manualImports[index] = manualImports[index].copy(
            path = update.path, seasonNumber = update.seasonNumber,
            episodes = update.episodes, quality = update.quality,
            languages = update.languages, releaseGroup = update.releaseGroup,
            customFormats = update.customFormats, indexerFlags = update.indexerFlags,
            releaseType = update.releaseType, rejections = update.rejections
        )
    }
}
return Unit''',
    ('Command', 'list'): 'return commandStore.values.toList()',
    ('Command', 'create'): '''val id = body.id.takeIf { it > 0 } ?: ((commandStore.keys.maxOrNull() ?: 0) + 1)
return body.copy(id = id).also { commandStore[id] = it }''',
    ('Command', 'delete'): 'commandStore.remove(id)\nreturn Unit',
    ('Command', 'get'): '''return commandStore[id]?.let { Response.success(it) }
    ?: Response.error(404, "Missing command".toResponseBody())''',
    ('Release', 'list'): '''return releases.filter {
    (seriesId == null || it.seriesId == seriesId) &&
        (episodeId == null || it.episodeId == episodeId ||
            episodeId in it.episodeIds.orEmpty()) &&
        (seasonNumber == null || it.seasonNumber == seasonNumber)
}''',
    ('Release', 'create'): 'grabbedReleases.add(body)\nreturn Unit',
    ('ReleasePush', 'create'): 'releases.add(body)\npushedReleases.add(body)\nreturn listOf(body)',
    ('Parse', 'list'): 'return parsedTitles[title ?: path] ?: ParseResource(title = title ?: path)',
    ('Ping', 'list'): '''return if (pingStatusCode in 200..299) Response.success(pingResource)
else Response.error(
    pingStatusCode, ArrJson.encodeToString(pingResource).toResponseBody()
)''',
    ('Ping', 'head'): '''return if (pingStatusCode in 200..299) Response.success(Unit)
else Response.error(pingStatusCode, "".toResponseBody())'''
}

fake_imports = {'import ' + PKG + '.model.' + n for n in OWNED}
fake_imports.update(['import dev.appolon.homelab.arr.net.ArrJson',
                     'import java.time.Instant',
                     'import kotlinx.serialization.json.JsonObject',
                     'import kotlinx.serialization.encodeToString',
                     'import kotlinx.serialization.json.encodeToJsonElement',
                     'import okhttp3.ResponseBody.Companion.toResponseBody',
                     'import retrofit2.HttpException', 'import retrofit2.Response'])
fake_header = '''/**
 * Mutable single-test state for all queue-group interfaces.
 * [latency] runs before [error], which can fail any operation before it mutates state.
 * Callbacks receive `Tag.method`. Projections retain seeded embeddings. Removal flags
 * are accepted without simulating an external download client or a filesystem.
 */
class FakeSonarrQueue(
    var latency: suspend (String) -> Unit = {},
    var error: (String) -> Throwable? = { null }
) {
    val queueStore: MutableMap<Int, QueueResource> = linkedMapOf()
    val blocklistStore: MutableMap<Int, BlocklistResource> = linkedMapOf()
    val historyStore: MutableMap<Int, HistoryResource> = linkedMapOf()
    val commandStore: MutableMap<Int, CommandResource> = linkedMapOf()
    val manualImports: MutableList<ManualImportResource> = mutableListOf()
    val reprocessedImports: MutableList<ManualImportReprocessResource> = mutableListOf()
    val releases: MutableList<ReleaseResource> = mutableListOf()
    val grabbedReleases: MutableList<ReleaseResource> = mutableListOf()
    val pushedReleases: MutableList<ReleaseResource> = mutableListOf()
    val grabbedQueueIds: MutableList<Int> = mutableListOf()
    val parsedTitles: MutableMap<String, ParseResource> = linkedMapOf()
    var pingResource: PingResource = PingResource(status = "OK")
    var pingStatusCode: Int = 200

    private suspend fun before(operation: String) {
        latency(operation)
        error(operation)?.let { throw it }
    }

    private fun missing(id: Int): Nothing = throw HttpException(
        Response.error<Any>(404, "Missing resource $id".toResponseBody())
    )

    private fun <T> paged(rows: List<T>, page: Int?, pageSize: Int?): List<T> {
        val size = (pageSize ?: 10).coerceAtLeast(0)
        val start = (((page ?: 1).coerceAtLeast(1) - 1).toLong() * size)
            .coerceAtMost(rows.size.toLong()).toInt()
        return rows.drop(start).take(size)
    }

    private inline fun <reified T> sorted(
        rows: List<T>, key: String?, direction: SortDirection?
    ): List<T> {
        if (key == null) return rows
        val result = rows.sortedWith { left, right ->
            val a = (ArrJson.encodeToJsonElement(left) as JsonObject)[key]
            val b = (ArrJson.encodeToJsonElement(right) as JsonObject)[key]
            val x = a?.toString()?.trim('"').orEmpty()
            val y = b?.toString()?.trim('"').orEmpty()
            val xn = x.toDoubleOrNull()
            val yn = y.toDoubleOrNull()
            if (xn != null && yn != null) xn.compareTo(yn) else x.compareTo(y)
        }
        return if (direction == SortDirection.DESCENDING) result.reversed() else result
    }
'''
fake_parts = []
for tag, ops in OPERATIONS.items():
    functions = []
    for o in ops:
        params = ',\n'.join('            ' + n + ': ' + t for n, t, _, _ in o['params'])
        signature = '        override suspend fun ' + o['name'] + '(' + (
            '\n' + params + '\n        ' if params else '') + '): ' + o['result'] + ' {\n'
        body = FAKE_BODIES[(tag, o['name'])]
        functions.append(signature + '            before("' + tag + '.' + o['name'] + '")\n' +
                         '\n'.join('            ' + l for l in body.splitlines()) + '\n        }')
    fake_parts.append('    val ' + tag[0].lower() + tag[1:] + ': ' + tag + 'Api = object : ' + tag +
                      'Api {\n' + '\n\n'.join(functions) + '\n    }')
write('queue', 'kotlin/' + PKG.replace('.', '/') + '/FakeSonarrQueue.kt',
      'package ' + PKG + '\n\n' + '\n'.join(sorted(fake_imports)) + '\n\n' + fake_header +
      '\n' + '\n\n'.join(fake_parts) + '\n}\n')
