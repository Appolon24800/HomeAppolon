"""Generate the queue group's wire contracts and schema-derived test cases."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
GROUP = Path(__file__).resolve().parent
SPEC = json.loads((ROOT / 'docs/arr-api/radarr.openapi.json').read_text())
SCHEMAS = SPEC['components']['schemas']
PACKAGE = 'dev.appolon.homelab.arr.api.radarr'
MAIN = GROUP / 'kotlin' / Path(*PACKAGE.split('.'))
TEST = GROUP / 'src/test/kotlin' / Path(*PACKAGE.split('.'))
FIXTURES = GROUP / 'src/test/resources/fixtures/radarr'
TAGS = ['Queue', 'QueueAction', 'QueueDetails', 'QueueStatus', 'Blocklist',
        'History', 'ManualImport', 'Command']
SHARED = {'MovieResource', 'Language', 'QualityModel', 'CustomFormatResource'}
OPERATIONS = []
NAMES = {
    ('get', '/api/v3/queue'): 'queue',
    ('delete', '/api/v3/queue/{id}'): 'removeQueue',
    ('delete', '/api/v3/queue/bulk'): 'removeQueueBulk',
    ('post', '/api/v3/queue/grab/{id}'): 'grabQueue',
    ('post', '/api/v3/queue/grab/bulk'): 'grabQueueBulk',
    ('get', '/api/v3/queue/details'): 'queueDetails',
    ('get', '/api/v3/queue/status'): 'queueStatus',
    ('get', '/api/v3/blocklist'): 'blocklist',
    ('get', '/api/v3/blocklist/movie'): 'movieBlocklist',
    ('delete', '/api/v3/blocklist/{id}'): 'removeBlocklist',
    ('delete', '/api/v3/blocklist/bulk'): 'removeBlocklistBulk',
    ('get', '/api/v3/history'): 'history',
    ('get', '/api/v3/history/since'): 'historySince',
    ('get', '/api/v3/history/movie'): 'movieHistory',
    ('post', '/api/v3/history/failed/{id}'): 'markHistoryFailed',
    ('get', '/api/v3/manualimport'): 'manualImport',
    ('post', '/api/v3/manualimport'): 'reprocessManualImport',
    ('get', '/api/v3/command'): 'commands',
    ('post', '/api/v3/command'): 'startCommand',
    ('get', '/api/v3/command/{id}'): 'command',
    ('delete', '/api/v3/command/{id}'): 'cancelCommand',
}


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def refs(value):
    if isinstance(value, dict):
        if '$ref' in value:
            yield value['$ref'].split('/')[-1]
        for child in value.values():
            yield from refs(child)
    elif isinstance(value, list):
        for child in value:
            yield from refs(child)


def typename(s):
    if '$ref' in s:
        return s['$ref'].split('/')[-1]
    t = s.get('type')
    if t == 'array':
        return 'List<' + typename(s['items']) + '>'
    if t == 'object' and isinstance(s.get('additionalProperties'), dict):
        v = s['additionalProperties']
        return 'Map<String, ' + typename(v) + ('?' if v.get('nullable') else '') + '>'
    return {'integer': 'Long' if s.get('format') == 'int64' else 'Int',
            'number': 'Double', 'boolean': 'Boolean', 'string': 'String'}.get(t, 'JsonObject')


def enum_name(value):
    return re.sub(r'(?<!^)(?=[A-Z])', '_', value).upper()


def default(s):
    if s.get('nullable'):
        return 'null'
    t = typename(s)
    if t in SCHEMAS and 'enum' in SCHEMAS[t]:
        return t + '.' + enum_name(SCHEMAS[t]['enum'][0])
    if t.startswith('List<'):
        return 'emptyList()'
    if t.startswith('Map<'):
        return 'emptyMap()'
    return {'Int': '0', 'Long': '0L', 'Double': '0.0', 'Boolean': 'false',
            'String': '""', 'JsonObject': 'JsonObject(emptyMap())'}.get(t, t + '()')


for path, methods in SPEC['paths'].items():
    for method, operation in methods.items():
        tag = next((t for t in operation.get('tags', []) if t in TAGS), None)
        if tag:
            OPERATIONS.append((tag, method, path, operation, NAMES[method, path]))

needed = set()
for _, _, _, operation, _ in OPERATIONS:
    needed.update(refs(operation))
pending = list(needed)
while pending:
    n = pending.pop()
    if n in SHARED:
        continue
    for child in refs(SCHEMAS[n]):
        if child not in needed:
            needed.add(child)
            pending.append(child)

for name in sorted(needed - SHARED):
    s = SCHEMAS[name]
    text = f'package {PACKAGE}.model\n\n'
    if 'enum' in s:
        text += 'import kotlinx.serialization.SerialName\n'
    text += 'import kotlinx.serialization.Serializable\n'
    if any(typename(v) == 'JsonObject' for v in s.get('properties', {}).values()):
        text += 'import kotlinx.serialization.json.JsonObject\n'
    text += f'\n/** Wire contract for Radarr {name}. Dates remain unparsed strings. */\n'
    text += '@Serializable\n'
    if 'enum' in s:
        text += f'enum class {name} {{\n'
        for value in s['enum']:
            text += f'    @SerialName("{value}") {enum_name(value)},\n'
        text += '    ;\n\n    override fun toString(): String = when (this) {\n'
        for value in s['enum']:
            text += f'        {enum_name(value)} -> "{value}"\n'
        text += '    }\n}\n'
    else:
        text += f'data class {name}(\n'
        for prop, field in s.get('properties', {}).items():
            nullable = '?' if field.get('nullable') else ''
            pname = '`' + prop + '`' if prop in {'object', 'when', 'is'} else prop
            text += f'    val {pname}: {typename(field)}{nullable} = {default(field)},\n'
        text += ')\n'
    write(MAIN / 'model' / (name + '.kt'), text)


def signature(operation):
    params = []
    for p in operation.get('parameters', []):
        required = p.get('required', False)
        s = p['schema']
        t = typename(s)
        suffix = '' if required else '?'
        annotation = 'Path' if p['in'] == 'path' else 'Query'
        params.append((f'@{annotation}("{p["name"]}") ', p['name'], t + suffix,
                       '' if required else ' = null'))
    body = operation.get('requestBody')
    if body:
        s = body['content']['application/json']['schema']
        params.append(('@Body ', 'body', typename(s), ''))
    return params


def response(operation):
    code = next(k for k in operation['responses'] if k.startswith('2'))
    content = operation['responses'][code].get('content')
    s = content['application/json']['schema'] if content else None
    t = typename(s) if s is not None else 'Unit'
    wrapped = len(operation['responses']) > 1 or t == 'Unit'
    return t, wrapped, code, s


for tag in TAGS:
    ops = [o for o in OPERATIONS if o[0] == tag]
    imports = set()
    for _, method, _, operation, _ in ops:
        imports.add('retrofit2.http.' + ('HTTP' if method == 'delete' and
                                       operation.get('requestBody') else method.upper()))
        for ann, _, _, _ in signature(operation):
            imports.add('retrofit2.http.' + ann.split('(')[0][1:].strip())
        t, wrapped, _, _ = response(operation)
        if wrapped:
            imports.add('retrofit2.Response')
        if t == 'JsonObject':
            imports.add('kotlinx.serialization.json.JsonObject')
        for n in refs(operation):
            imports.add(PACKAGE + '.model.' + n)
    text = f'package {PACKAGE}\n\n' + ''.join('import ' + i + '\n' for i in sorted(imports))
    text += f'\n/** Radarr endpoints tagged {tag}. */\ninterface {tag}Api {{\n'
    for _, method, path, operation, name in ops:
        t, wrapped, _, s = response(operation)
        if s == {}:
            text += '    /** The spec leaves the response schema unspecified. */\n'
        if method == 'delete' and operation.get('requestBody'):
            text += f'    @HTTP(method = "DELETE", path = "{path[1:]}", hasBody = true)\n'
        else:
            text += f'    @{method.upper()}("{path[1:]}")\n'
        ps = signature(operation)
        ret = f'Response<{t}>' if wrapped else t
        if ps:
            text += f'    suspend fun {name}(\n'
            for ann, pn, pt, d in ps:
                text += f'        {ann}{pn}: {pt}{d},\n'
            text += f'    ): {ret}\n\n'
        else:
            text += f'    suspend fun {name}(): {ret}\n\n'
    text += '}\n'
    write(MAIN / (tag + 'Api.kt'), text)

factory = f'package {PACKAGE}\n\nimport retrofit2.Retrofit\n\n'
factory += '/** Builds the queue, history, import, and command clients on a supplied connection. */\n'
factory += 'object RadarrQueueApis {\n'
for tag in TAGS:
    method = tag[0].lower() + tag[1:]
    factory += f'    fun {method}(retrofit: Retrofit): {tag}Api =\n'
    factory += f'        retrofit.create({tag}Api::class.java)\n\n'
factory += '}\n'
write(MAIN / 'RadarrQueueApis.kt', factory)


def example(s, depth=0):
    if 'example' in s:
        return s['example']
    if '$ref' in s:
        n = s['$ref'].split('/')[-1]
        return example(SCHEMAS[n], depth + 1)
    if 'enum' in s:
        return s['enum'][0]
    if s.get('nullable'):
        return None
    t = s.get('type')
    if t == 'object':
        if depth > 8:
            return {}
        return {k: example(v, depth + 1) for k, v in s.get('properties', {}).items()}
    if t == 'array':
        return [example(s['items'], depth + 1)]
    if t == 'string':
        return '2026-01-01T00:00:00Z' if s.get('format') == 'date-time' else (
            '00:01:00' if s.get('format') == 'date-span' else 'string')
    return {'integer': 1, 'number': 1.0, 'boolean': False}.get(t, {})


for tag, method, path, op, name in OPERATIONS:
    _, _, _, schema = response(op)
    if schema is not None:
        value = example(schema)
        if isinstance(value, dict) and 'records' in value:
            paging = SCHEMAS[schema['$ref'].split('/')[-1]]
            value['records'] = [example(paging['properties']['records']['items'])]
        write(FIXTURES / tag.lower() / (name + '.json'), json.dumps(value, indent=2) + '\n')

test = f'''package {PACKAGE}

import dev.appolon.homelab.arr.api.radarr.model.*
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Each spec operation has its own fixture and verifies the base-path prefix. */
class RadarrQueueContractTest {{
    private fun retrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/radarr/"))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun fixture(tag: String, name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(
            "fixtures/radarr/$tag/$name.json"
        )).bufferedReader().use {{ it.readText() }}

'''
for tag, method, path, op, name in OPERATIONS:
    t, wrapped, code, schema = response(op)
    args = []
    for ann, pn, pt, _ in signature(op):
        if ann == '@Body ':
            args.append('emptyList()' if pt.startswith('List<') else pt + '()')
        else:
            base = pt.rstrip('?')
            val = ('listOf(1, 2)' if base == 'List<Int>' else
                   'listOf(QueueStatus.QUEUED)' if base == 'List<QueueStatus>' else
                   'listOf(DownloadProtocol.TORRENT)' if base == 'List<DownloadProtocol>' else
                   '1' if base == 'Int' else 'true' if base == 'Boolean' else
                   '"string"' if base == 'String' else base + '.' + enum_name(SCHEMAS[base]['enum'][0]))
            args.append(pn + ' = ' + val)
    call = 'api.' + name + '(' + (
        '\n' + ''.join('                ' + arg + ',\n' for arg in args) + '            '
        if args else '') + ')'
    test += f'    @Test fun {name}HappyPath() = runTest {{\n'
    test += '        val server = MockWebServer()\n        try {\n'
    test += f'            server.enqueue(MockResponse().setResponseCode({code})'
    if schema is not None:
        test += f'\n                .setBody(fixture("{tag.lower()}", "{name}"))'
    test += ')\n'
    test += f'            val api = RadarrQueueApis.{tag[0].lower() + tag[1:]}(retrofit(server))\n'
    test += f'            val result = {call}\n'
    if wrapped:
        test += f'            assertEquals({code}, result.code())\n'
    elif t.startswith('List<'):
        test += '            assertEquals(1, result.size)\n'
    elif t.endswith('PagingResource'):
        test += '            assertEquals(1, result.totalRecords)\n'
        test += '            assertEquals(1, result.records!!.single().id)\n'
    elif t == 'QueueStatusResource':
        test += '            assertEquals(1, result.totalCount)\n'
    else:
        test += '            assertNotNull(result)\n'
    test += '            val request = server.takeRequest()\n'
    test += f'            assertEquals("{method.upper()}", request.method)\n'
    test += f'            assertEquals("/radarr{path.replace("{id}", "1")}",\n'
    test += '                request.requestUrl!!.encodedPath)\n'
    for p in op.get('parameters', []):
        if p['in'] == 'query':
            base = typename(p['schema'])
            expected = ('listOf("1", "2")' if base == 'List<Int>' else
                        'listOf("queued")' if base == 'List<QueueStatus>' else
                        'listOf("torrent")' if base == 'List<DownloadProtocol>' else
                        'listOf("1")' if base == 'Int' else 'listOf("true")' if base == 'Boolean' else
                        'listOf("string")' if base == 'String' else
                        'listOf("' + SCHEMAS[base]['enum'][0] + '")')
            test += f'            assertEquals({expected},\n'
            test += f'                request.requestUrl!!.queryParameterValues("{p["name"]}"))\n'
    if op.get('requestBody'):
        bodytype = signature(op)[-1][2]
        expected = '[]' if bodytype.startswith('List<') else None
        if expected:
            test += '            assertEquals("[]", request.body.readUtf8())\n'
        else:
            test += f'            assertEquals({bodytype}(), ArrJson.decodeFromString<{bodytype}>(\n'
            test += '                request.body.readUtf8()))\n'
    test += '        } finally {\n            server.shutdown()\n        }\n    }\n\n'

for tag in TAGS:
    _, _, _, op, name = next(o for o in OPERATIONS if o[0] == tag)
    t, wrapped, _, _ = response(op)
    args = []
    for ann, pn, pt, _ in signature(op):
        if ann == '@Body ':
            args.append('body = ' + ('emptyList()' if pt.startswith('List<') else pt + '()'))
        elif not pt.endswith('?'):
            args.append(pn + ' = 1')
    call = 'api.' + name + '(' + ', '.join(args) + ')'
    test += f'    @Test fun {tag[0].lower() + tag[1:]}ErrorPath() = runTest {{\n'
    test += '        val server = MockWebServer()\n        try {\n'
    test += '            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))\n'
    test += f'            val api = RadarrQueueApis.{tag[0].lower() + tag[1:]}(retrofit(server))\n'
    if wrapped:
        test += f'            assertEquals(503, {call}.code())\n'
    else:
        test += f'            try {{\n                {call}\n'
        test += '                fail("Expected HTTP failure")\n'
        test += '            } catch (error: HttpException) {\n'
        test += '                assertEquals(503, error.code())\n            }\n'
    test += '        } finally {\n            server.shutdown()\n        }\n    }\n\n'
test += '}\n'
# Expand imports so generated tests follow the repository's no-wildcard convention.
models = sorted(needed)
test = test.replace('import ' + PACKAGE + '.model.*', '\n'.join(
    'import ' + PACKAGE + '.model.' + n for n in models))
test = test.replace('import org.junit.Assert.*', '\n'.join(
    'import org.junit.Assert.' + n for n in ['assertEquals', 'assertNotNull', 'fail']))
write(TEST / 'RadarrQueueContractTest.kt', test)
print(f'Generated {len(OPERATIONS)} operations and {len(needed - SHARED)} schema contracts.')
