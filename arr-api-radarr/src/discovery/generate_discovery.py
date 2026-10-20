"""Generate the discovery group's wire contracts and schema-derived test cases.

Owned tags: ImportList, ImportListConfig, ImportListExclusion, ImportListMovies,
Collection, CustomFilter, Missing, Cutoff. Types already defined by other groups
(src/main and src/queue model packages) are referenced, never redeclared.
"""
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
TAGS = ['ImportList', 'ImportListConfig', 'ImportListExclusion', 'ImportListMovies',
        'Collection', 'CustomFilter', 'Missing', 'Cutoff']
# Already compiled by the library and queue groups; same package, so no imports needed.
SHARED = {
    'AlternativeTitleResource', 'ApplyTags', 'AddMovieMethod', 'AddMovieOptions',
    'CustomFormatResource', 'CustomFormatSpecificationSchema', 'ExtraFileResource',
    'Field', 'Language', 'MediaCover', 'MediaCoverTypes', 'MediaInfoResource',
    'Modifier', 'MonitorTypes', 'MovieCollectionResource', 'MovieEditorResource',
    'MovieFileResource', 'MovieFolderResource', 'MovieResource',
    'MovieStatisticsResource', 'MovieStatusType', 'PrivacyLevel', 'Quality',
    'QualityModel', 'QualitySource', 'RatingChild', 'RatingType', 'Ratings',
    'Revision', 'SelectOption', 'SortDirection', 'SourceType',
}
OPERATIONS = []
NAMES = {
    ('get', '/api/v3/importlist'): 'importLists',
    ('post', '/api/v3/importlist'): 'createImportList',
    ('put', '/api/v3/importlist/{id}'): 'updateImportList',
    ('delete', '/api/v3/importlist/{id}'): 'removeImportList',
    ('get', '/api/v3/importlist/{id}'): 'importList',
    ('put', '/api/v3/importlist/bulk'): 'updateImportListBulk',
    ('delete', '/api/v3/importlist/bulk'): 'removeImportListBulk',
    ('get', '/api/v3/importlist/schema'): 'importListSchema',
    ('post', '/api/v3/importlist/test'): 'testImportList',
    ('post', '/api/v3/importlist/testall'): 'testAllImportLists',
    ('post', '/api/v3/importlist/action/{name}'): 'importListAction',
    ('get', '/api/v3/config/importlist'): 'importListConfig',
    ('put', '/api/v3/config/importlist/{id}'): 'updateImportListConfig',
    ('get', '/api/v3/config/importlist/{id}'): 'importListConfigById',
    ('get', '/api/v3/exclusions'): 'exclusions',
    ('post', '/api/v3/exclusions'): 'createExclusion',
    ('get', '/api/v3/exclusions/paged'): 'exclusionsPaged',
    ('put', '/api/v3/exclusions/{id}'): 'updateExclusion',
    ('delete', '/api/v3/exclusions/{id}'): 'removeExclusion',
    ('get', '/api/v3/exclusions/{id}'): 'exclusion',
    ('post', '/api/v3/exclusions/bulk'): 'addExclusionsBulk',
    ('delete', '/api/v3/exclusions/bulk'): 'removeExclusionsBulk',
    ('get', '/api/v3/importlist/movie'): 'importListMovies',
    ('post', '/api/v3/importlist/movie'): 'addMovies',
    ('get', '/api/v3/collection'): 'collections',
    ('put', '/api/v3/collection'): 'updateCollections',
    ('put', '/api/v3/collection/{id}'): 'updateCollection',
    ('get', '/api/v3/collection/{id}'): 'collection',
    ('get', '/api/v3/customfilter'): 'customFilters',
    ('post', '/api/v3/customfilter'): 'createCustomFilter',
    ('put', '/api/v3/customfilter/{id}'): 'updateCustomFilter',
    ('delete', '/api/v3/customfilter/{id}'): 'removeCustomFilter',
    ('get', '/api/v3/customfilter/{id}'): 'customFilter',
    ('get', '/api/v3/wanted/missing'): 'missing',
    ('get', '/api/v3/wanted/cutoff'): 'cutoff',
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


def enum_name(value):
    return re.sub(r'(?<!^)(?=[A-Z])', '_', value).upper()


def klass(s):
    """Map a schema to (Kotlin type, nullable). $ref fields stay nullable per the
    module convention documented in MovieResource.kt: OpenAPI 3.0 cannot mark
    nullability on a $ref sibling and Radarr omits absent objects."""
    if '$ref' in s:
        return s['$ref'].split('/')[-1], True
    t = s.get('type')
    if t == 'array':
        inner, _ = klass(s['items'])
        coll = 'Set' if s.get('uniqueItems') and inner == 'Int' else 'List'
        return coll + '<' + inner + '>', bool(s.get('nullable'))
    if t == 'object' and isinstance(s.get('additionalProperties'), dict):
        return 'Map<String, JsonElement>', True
    if t == 'integer':
        return ('Long' if s.get('format') == 'int64' else 'Int'), bool(s.get('nullable'))
    if t == 'number':
        return 'Double', bool(s.get('nullable'))
    if t == 'boolean':
        return 'Boolean', bool(s.get('nullable'))
    if t == 'string':
        return 'String', bool(s.get('nullable'))
    # Schema-free values (Field.value, ValidationFailure.attemptedValue, ...).
    return 'JsonElement', True


def default(ktype, nullable):
    if nullable:
        return 'null'
    scalar = {'Int': '0', 'Long': '0L', 'Double': '0.0', 'Boolean': 'false',
              'String': '""', 'List': 'emptyList()', 'Set': 'emptySet()',
              'Map': 'emptyMap()', 'JsonElement': 'JsonNull'}
    return scalar.get(ktype.split('<')[0], ktype + '()')


for path, methods in SPEC['paths'].items():
    for method, operation in methods.items():
        if not isinstance(operation, dict):
            continue
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
    text = 'package ' + PACKAGE + '.model\n\n'
    if 'enum' in s:
        text += 'import kotlinx.serialization.SerialName\nimport kotlinx.serialization.Serializable\n'
        text += f'\n/** Radarr {name} wire enum, constants in spec order. */\n'
        text += '@Serializable\nenum class ' + name + ' {\n'
        for value in s['enum']:
            text += f'    @SerialName("{value}") {enum_name(value)},\n'
        text += '    ;\n\n    override fun toString(): String = when (this) {\n'
        for value in s['enum']:
            text += f'        {enum_name(value)} -> "{value}"\n'
        text += '    }\n}\n'
    else:
        uses_element = any('JsonElement' in klass(v)[0]
                           for v in s.get('properties', {}).values())
        text += 'import kotlinx.serialization.Serializable\n'
        if uses_element:
            text += 'import kotlinx.serialization.json.JsonElement\n'
        text += f'\n/** Wire contract for Radarr {name}.\n * Dates remain unparsed strings. */\n'
        text += '@Serializable\ndata class ' + name + '(\n'
        for prop, field in s.get('properties', {}).items():
            ktype, nullable = klass(field)
            pname = '`' + prop + '`' if prop in {'object', 'when', 'is'} else prop
            text += f'    val {pname}: {ktype}{"?" if nullable else ""} = ' \
                    f'{default(ktype, nullable)},\n'
        text += ')\n'
    write(MAIN / 'model' / (name + '.kt'), text)


def signature(operation):
    params = []
    for p in operation.get('parameters', []):
        s = p['schema']
        ktype, _ = klass(s)
        required = p.get('required', False)
        annotation = 'Path' if p['in'] == 'path' else 'Query'
        params.append((f'@{annotation}("{p["name"]}") ', p['name'],
                       ktype + ('' if required else '?'), '' if required else ' = null'))
    body = operation.get('requestBody')
    if body:
        s = body['content']['application/json']['schema']
        params.append(('@Body ', 'body', klass(s)[0].replace('Set<', 'List<'), ''))
    return params


def response(operation):
    ok = [k for k in operation['responses'] if k.startswith('2')]
    # POST /importlist/action/{name} documents only a 400; treat success as Unit.
    code = int(ok[0]) if ok else 200
    content = (operation['responses'][ok[0]].get('content')
               if ok else None)
    s = content['application/json']['schema'] if content else None
    if s == {}:
        ktype = 'JsonObject'
    elif s is None:
        ktype = 'Unit'
    else:
        ktype = klass(s)[0].replace('Set<', 'List<')
    wrapped = not ok or len(operation['responses']) > 1 or ktype == 'Unit'
    return ktype, wrapped, code, s


for tag in TAGS:
    ops = [o for o in OPERATIONS if o[0] == tag]
    imports = set()
    for _, method, _, operation, _ in ops:
        imports.add('retrofit2.http.' + ('HTTP' if method == 'delete' and
                                         operation.get('requestBody') else method.upper()))
        for ann, _, _, _ in signature(operation):
            imports.add('retrofit2.http.' + ann.split('(')[0][1:].strip())
        ktype, wrapped, _, _ = response(operation)
        if wrapped:
            imports.add('retrofit2.Response')
        if ktype == 'JsonObject':
            imports.add('kotlinx.serialization.json.JsonObject')
        for n in refs(operation):
            imports.add(PACKAGE + '.model.' + n)
    text = 'package ' + PACKAGE + '\n\n' + ''.join('import ' + i + '\n' for i in sorted(imports))
    text += f'\n/** Radarr endpoints tagged {tag}. */\ninterface {tag}Api {{\n'
    for _, method, path, operation, name in ops:
        ktype, wrapped, _, s = response(operation)
        if s == {}:
            text += '    /** The spec leaves the response schema unspecified. */\n'
        elif 'responses' in operation and not [k for k in operation['responses'] if k.startswith('2')]:
            text += '    /** The spec documents only a 400 response, so success carries no body. */\n'
        if method == 'delete' and operation.get('requestBody'):
            text += f'    @HTTP(method = "DELETE", path = "{path[1:]}", hasBody = true)\n'
        else:
            text += f'    @{method.upper()}("{path[1:]}")\n'
        ps = signature(operation)
        ret = 'Response<' + ktype + '>' if wrapped else ktype
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
factory += '/** Builds the import list, discovery, collection, and wanted clients on a connection. */\n'
factory += 'object RadarrImportlistApis {\n'
for tag in TAGS:
    method = tag[0].lower() + tag[1:]
    factory += f'    fun {method}(retrofit: Retrofit): {tag}Api =\n'
    factory += f'        retrofit.create({tag}Api::class.java)\n\n'
factory += '}\n'
write(MAIN / 'RadarrImportlistApis.kt', factory)


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
        return '2026-01-01T00:00:00Z' if s.get('format') == 'date-time' else 'string'
    return {'integer': 1, 'number': 1.0, 'boolean': False}.get(t, {})


for tag, method, path, op, name in OPERATIONS:
    _, _, _, schema = response(op)
    if schema is not None:
        write(FIXTURES / tag.lower() / (name + '.json'),
              json.dumps(example(schema), indent=2) + '\n')

QUERY_VALUE = {
    'Int': '1', 'Boolean': 'true', 'String': 'string',
    'SortDirection': 'default',
}
ARG_VALUE = {
    'Int': '1', 'Boolean': 'true', 'String': '"string"',
    'SortDirection': 'SortDirection.DEFAULT',
}

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
class RadarrDiscoveryContractTest {{
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
    ktype, wrapped, code, schema = response(op)
    args = []
    for ann, pn, pt, _ in signature(op):
        base = pt.rstrip('?')
        if ann == '@Body ':
            args.append('emptyList()' if base.startswith('List<') else base + '()')
        elif pn == 'name':
            args.append(pn + ' = "string"')
        elif ann.startswith('@Path') and base == 'String':
            args.append(pn + ' = "1"')
        else:
            args.append(pn + ' = ' + ARG_VALUE[base])
    call_inline = 'api.' + name + '(' + ', '.join(args) + ')'
    test += f'    @Test fun {name}HappyPath() = runTest {{\n'
    test += '        val server = MockWebServer()\n        try {\n'
    test += f'            server.enqueue(MockResponse().setResponseCode({code})'
    if schema is not None:
        test += f'\n                .setBody(fixture("{tag.lower()}", "{name}"))'
    test += ')\n'
    test += f'            val api = RadarrImportlistApis.{tag[0].lower() + tag[1:]}(retrofit(server))\n'
    if len('            val result = ' + call_inline) <= 100:
        test += f'            val result = {call_inline}\n'
    else:
        test += '            val result = api.' + name + '(\n'
        for arg in args:
            test += f'                {arg},\n'
        test += '            )\n'
    if wrapped:
        test += f'            assertEquals({code}, result.code())\n'
    elif ktype.startswith('List<'):
        test += '            assertEquals(1, result.size)\n'
    elif ktype.endswith('PagingResource'):
        test += '            assertEquals(1, result.totalRecords)\n'
    else:
        test += '            assertNotNull(result)\n'
    test += '            val request = server.takeRequest()\n'
    test += f'            assertEquals("{method.upper()}", request.method)\n'
    if '{id}' in path:
        test += '            assertEquals("/radarr' + path.replace('{id}', '1') + '",\n'
    elif '{name}' in path:
        test += '            assertEquals("/radarr' + path.replace('{name}', 'string') + '",\n'
    else:
        test += '            assertEquals("/radarr' + path + '",\n'
    test += '                request.requestUrl!!.encodedPath)\n'
    for p in op.get('parameters', []):
        if p['in'] == 'query':
            base = klass(p['schema'])[0]
            test += f'            assertEquals(listOf("{QUERY_VALUE[base]}"),\n'
            test += f'                request.requestUrl!!.queryParameterValues("{p["name"]}"))\n'
    if op.get('requestBody'):
        bodytype = signature(op)[-1][2]
        if bodytype.startswith('List<'):
            test += '            assertEquals("[]", request.body.readUtf8())\n'
        else:
            test += '            val decoded = ArrJson.decodeFromString<' + bodytype + '>(\n'
            test += '                request.body.readUtf8())\n'
            test += f'            assertEquals({bodytype}(), decoded)\n'
    test += '        } finally {\n            server.shutdown()\n        }\n    }\n\n'

for tag in TAGS:
    _, _, _, op, name = next(o for o in OPERATIONS if o[0] == tag)
    ktype, wrapped, _, _ = response(op)
    args = []
    for ann, pn, pt, _ in signature(op):
        if ann == '@Body ':
            args.append('body = ' + ('emptyList()' if pt.startswith('List<') else pt + '()'))
        elif not pt.endswith('?'):
            args.append(pn + ' = ' + ('"string"' if pn == 'name' else '1'))
    call = 'api.' + name + '(' + ', '.join(args) + ')'
    test += f'    @Test fun {tag[0].lower() + tag[1:]}ErrorPath() = runTest {{\n'
    test += '        val server = MockWebServer()\n        try {\n'
    test += '            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))\n'
    test += f'            val api = RadarrImportlistApis.{tag[0].lower() + tag[1:]}(retrofit(server))\n'
    if wrapped:
        if len('            assertEquals(503, ' + call + '.code())') <= 100:
            test += f'            assertEquals(503, {call}.code())\n'
        else:
            test += '            assertEquals(503, ' + call + '.\n                code())\n'
    else:
        test += '            try {\n                ' + call + '\n'
        test += '                fail("Expected HTTP failure")\n'
        test += '            } catch (error: HttpException) {\n'
        test += '                assertEquals(503, error.code())\n            }\n'
    test += '        } finally {\n            server.shutdown()\n        }\n    }\n\n'
test += '}\n'
models = sorted(n for n in needed if n not in SHARED)
used_shared = sorted(n for n in SHARED if re.search(r'\b' + n + r'\.', test))
test = test.replace('import ' + PACKAGE + '.model.*', '\n'.join(
    'import ' + PACKAGE + '.model.' + n for n in models + used_shared))
test = test.replace('import org.junit.Assert.*', '\n'.join(
    'import org.junit.Assert.' + n for n in ['assertEquals', 'assertNotNull', 'fail']))
write(TEST / 'RadarrDiscoveryContractTest.kt', test)
print(f'Generated {len(OPERATIONS)} operations and {len(models)} schema contracts.')
