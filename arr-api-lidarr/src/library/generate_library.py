"""Regenerate library contracts and schema-derived fixtures from the checked-in OpenAPI."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
BASE = Path(__file__).resolve().parent
SPEC = json.loads((ROOT / 'docs/arr-api/lidarr.openapi.json').read_text())
SCHEMAS = SPEC['components']['schemas']
PACKAGE = 'dev.appolon.homelab.arr.api.lidarr'
MAIN = BASE / 'kotlin' / PACKAGE.replace('.', '/')
TEST = BASE / 'src/test/kotlin' / PACKAGE.replace('.', '/')
RES = BASE / 'src/test/resources/fixtures/lidarr'

# Explicit names keep the API stable when paths are reordered in the specification.
OPERATIONS = {
    ('Album', 'get', '/api/v1/album'): 'getAlbums',
    ('Album', 'post', '/api/v1/album'): 'addAlbum',
    ('Album', 'get', '/api/v1/album/{id}'): 'getAlbum',
    ('Album', 'put', '/api/v1/album/{id}'): 'updateAlbum',
    ('Album', 'delete', '/api/v1/album/{id}'): 'deleteAlbum',
    ('Album', 'put', '/api/v1/album/monitor'): 'monitorAlbums',
    ('AlbumLookup', 'get', '/api/v1/album/lookup'): 'lookupAlbums',
    ('AlbumStudio', 'post', '/api/v1/albumstudio'): 'updateAlbumStudio',
    ('Artist', 'get', '/api/v1/artist'): 'getArtists',
    ('Artist', 'post', '/api/v1/artist'): 'addArtist',
    ('Artist', 'get', '/api/v1/artist/{id}'): 'getArtist',
    ('Artist', 'put', '/api/v1/artist/{id}'): 'updateArtist',
    ('Artist', 'delete', '/api/v1/artist/{id}'): 'deleteArtist',
    ('ArtistEditor', 'put', '/api/v1/artist/editor'): 'updateArtists',
    ('ArtistEditor', 'delete', '/api/v1/artist/editor'): 'deleteArtists',
    ('ArtistLookup', 'get', '/api/v1/artist/lookup'): 'lookupArtists',
    ('MediaCover', 'get', '/api/v1/mediacover/artist/{artistId}/{filename}'): 'getArtistCover',
    ('MediaCover', 'get', '/api/v1/mediacover/album/{albumId}/{filename}'): 'getAlbumCover',
    ('RenameTrack', 'get', '/api/v1/rename'): 'getTrackRenames',
    ('RetagTrack', 'get', '/api/v1/retag'): 'getTrackRetags',
    ('Track', 'get', '/api/v1/track'): 'getTracks',
    ('Track', 'get', '/api/v1/track/{id}'): 'getTrack',
    ('TrackFile', 'get', '/api/v1/trackfile'): 'getTrackFiles',
    ('TrackFile', 'get', '/api/v1/trackfile/{id}'): 'getTrackFile',
    ('TrackFile', 'put', '/api/v1/trackfile/{id}'): 'updateTrackFile',
    ('TrackFile', 'delete', '/api/v1/trackfile/{id}'): 'deleteTrackFile',
    ('TrackFile', 'put', '/api/v1/trackfile/editor'): 'updateTrackFiles',
    ('TrackFile', 'delete', '/api/v1/trackfile/bulk'): 'deleteTrackFiles',
}
TAGS = {key[0] for key in OPERATIONS}


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.rstrip() + '\n')


def ref(schema):
    return schema['$ref'].split('/')[-1]


def kt(schema):
    if '$ref' in schema:
        return ref(schema)
    typ = schema.get('type')
    if typ == 'array':
        return f"List<{kt(schema['items'])}>"
    if typ == 'integer':
        return 'Long' if schema.get('format') == 'int64' else 'Int'
    if typ == 'number':
        return 'Float' if schema.get('format') == 'float' else 'Double'
    return {'boolean': 'Boolean', 'string': 'String', 'object': 'JsonObject'}.get(
        typ, 'JsonElement')


def default(schema):
    if schema.get('nullable'):
        return '?', 'null'
    if '$ref' in schema:
        name = ref(schema)
        if 'enum' in SCHEMAS[name]:
            return '', f'{name}.{SCHEMAS[name]["enum"][0].upper()}'
        # Optional references may be absent. Null avoids recursive artist/album defaults.
        return '?', 'null'
    typ = kt(schema)
    return '', {'Int': '0', 'Long': '0L', 'Double': '0.0', 'Float': '0f',
                'Boolean': 'false', 'String': '""'}.get(typ, 'emptyList()')


seen = set()


def model(schema):
    if isinstance(schema, list):
        for value in schema:
            model(value)
    if not isinstance(schema, dict):
        return
    if '$ref' in schema:
        name = ref(schema)
        if name in seen:
            return
        seen.add(name)
        definition = SCHEMAS[name]
        header = f'package {PACKAGE}.model\n\n'
        header += 'import kotlinx.serialization.SerialName\nimport kotlinx.serialization.Serializable\n'
        if 'enum' in definition:
            text = '\n/** Values enumerated by the Lidarr OpenAPI schema. */\n'
            text += f'@Serializable\nenum class {name} {{\n'
            text += '\n'.join(f'    @SerialName("{value}") {value.upper()},'
                              for value in definition['enum']) + '\n}\n'
        else:
            header += ('import kotlinx.serialization.json.JsonElement\n'
                       'import kotlinx.serialization.json.JsonObject\n')
            text = f'\n/** Lidarr {name} wire resource. Dates remain unparsed strings. */\n'
            text += f'@Serializable\ndata class {name}(\n'
            for field, prop in definition.get('properties', {}).items():
                suffix, value = default(prop)
                field_name = f'`{field}`' if field in {'explicit', 'object', 'when', 'is'} else field
                text += f'    val {field_name}: {kt(prop)}{suffix} = {value},\n'
            text += ')\n'
        write(MAIN / 'model' / f'{name}.kt', header + text)
        model(definition)
    for value in schema.values():
        model(value)


def content_schema(container):
    content = container.get('content', {})
    return next((value['schema'] for value in content.values() if 'schema' in value), None)


def example(schema, stack=()):
    if schema is None:
        return {}
    if 'example' in schema:
        return schema['example']
    if '$ref' in schema:
        name = ref(schema)
        if name in stack:
            return {}
        return example(SCHEMAS[name], stack + (name,))
    if 'enum' in schema:
        return schema['enum'][0]
    if schema.get('type') == 'object':
        return {name: example(prop, stack) for name, prop in schema.get('properties', {}).items()
                if '$ref' not in prop or ref(prop) not in stack}
    if schema.get('type') == 'array':
        return [example(schema['items'], stack)]
    if schema.get('nullable'):
        return None
    return {'integer': 1, 'number': 1.0, 'boolean': False,
            'string': '2026-01-01T00:00:00Z' if schema.get('format') == 'date-time'
            else '00:00:01' if schema.get('format') == 'date-span' else 'string'}.get(
                schema.get('type'), None)


groups = {}
for path, methods in SPEC['paths'].items():
    for method, operation in methods.items():
        for tag in TAGS.intersection(operation.get('tags', [])):
            key = (tag, method, path)
            assert key in OPERATIONS, f'Uncovered operation {key}'
            groups.setdefault(tag, []).append((method, path, operation, OPERATIONS[key]))
assert sum(map(len, groups.values())) == len(OPERATIONS)

for tag, operations in sorted(groups.items()):
    code = f'package {PACKAGE}\n\n'
    # Explicit imports avoid model ownership leaking into other tag groups.
    names = set()
    for method, path, operation, name in operations:
        for schema in [content_schema(operation.get('requestBody', {})),
                       content_schema(operation['responses']['200'])]:
            if schema:
                model(schema)
                if '$ref' in schema:
                    names.add(ref(schema))
                elif schema.get('type') == 'array':
                    names.add(ref(schema['items']))
    code += ''.join(f'import {PACKAGE}.model.{name}\n' for name in sorted(names))
    code += 'import kotlinx.serialization.json.JsonObject\n'
    for annotation in ['Body', 'DELETE', 'GET', 'HTTP', 'POST', 'PUT', 'Path', 'Query']:
        code += f'import retrofit2.http.{annotation}\n'
    code += f'\n/** {tag} endpoints from the checked-in Lidarr specification. */\n'
    code += f'interface {tag}Api {{\n'
    tests = ''
    for method, path, operation, name in operations:
        body = content_schema(operation.get('requestBody', {}))
        response = content_schema(operation['responses']['200'])
        if response is None:
            code += '    /** The specification omits the response schema. */\n'
        if method == 'delete' and body:
            code += f'    @HTTP(method = "DELETE", path = "{path[1:]}", hasBody = true)\n'
        else:
            code += f'    @{method.upper()}("{path[1:]}")\n'
        code += f'    suspend fun {name}(\n'
        params = []
        # Retrofit rejects @Path parameters after a @Query parameter.
        for param in sorted(operation.get('parameters', []),
                            key=lambda item: item['in'] != 'path'):
            required = param.get('required', False)
            typ = kt(param['schema']) + ('' if required else '?')
            annotation = param['in'].title()
            code += (f'        @{annotation}("{param["name"]}") {param["name"]}: {typ}'
                     + (',' if required else ' = null,') + '\n')
            value = {'Int': '1', 'String': '"cover.jpg"' if param['name'] == 'filename'
                     else '"1"', 'Boolean': 'true'}.get(kt(param['schema']), 'listOf(1, 2)')
            params.append(f'{param["name"]} = {value}')
        if body:
            code += f'        @Body body: {kt(body)},\n'
            params.append(f'body = {kt(body)}()')
        result = kt(response) if response else 'JsonObject'
        code += f'    ): {result}\n\n'
        fixture_path = f'{tag.lower()}/{name}.json'
        write(RES / fixture_path, json.dumps(example(response), indent=2))
        call = f'api.{name}(\n' + ''.join(f'            {p},\n' for p in params) + '        )'
        tests += f'    @Test\n    fun {name}HappyPath() = runTest {{\n'
        tests += f'        val fixture = fixture("{fixture_path}")\n'
        tests += '        server.enqueue(MockResponse().setBody(fixture))\n'
        tests += f'        val actual = {call}\n'
        tests += f'        assertEquals(ArrJson.decodeFromString<{result}>(fixture), actual)\n'
        tests += '        val request = server.takeRequest()\n'
        tests += f'        assertEquals("{method.upper()}", request.method)\n'
        expected_path = path
        for param in operation.get('parameters', []):
            if param['in'] == 'path':
                expected_path = expected_path.replace('{' + param['name'] + '}',
                    'cover.jpg' if param['name'] == 'filename' else '1')
        tests += (f'        assertEquals(\n            "/lidarr{expected_path}",\n'
                  '            request.requestUrl!!.encodedPath,\n        )\n')
        for param in operation.get('parameters', []):
            if param['in'] == 'query':
                val = kt(param['schema'])
                if val.startswith('List'):
                    tests += (f'        assertEquals(listOf("1", "2"), request.requestUrl!!\n'
                              f'            .queryParameterValues("{param["name"]}"))\n')
                else:
                    value = 'true' if val == 'Boolean' else '1'
                    tests += (f'        assertEquals("{value}", request.requestUrl!!\n'
                              f'            .queryParameter("{param["name"]}"))\n')
        if body:
            tests += (f'        assertEquals({kt(body)}(), ArrJson.decodeFromString<{kt(body)}>(\n'
                      '            request.body.readUtf8()))\n')
        tests += '    }\n\n'
    write(MAIN / f'{tag}Api.kt', code + '}')
    first = operations[0]
    first_op = first[2]
    args = [f'{p["name"]} = ' + ('1' if kt(p['schema']) == 'Int' else '"cover.jpg"')
            for p in first_op.get('parameters', []) if p.get('required')]
    body = content_schema(first_op.get('requestBody', {}))
    if body:
        args.append(f'body = {kt(body)}()')
    test_header = f'package {PACKAGE}\n\n'
    test_header += ''.join(f'import {PACKAGE}.model.{n}\n' for n in sorted(names))
    test_header += '''import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
'''
    test_header += f'\nclass {tag}LibraryApiTest {{\n'
    test_header += '    private val server = MockWebServer()\n'
    test_header += f'    private lateinit var api: {tag}Api\n\n'
    factory_name = tag[0].lower() + tag[1:]
    test_header += f'''    @Before
    fun setUp() {{
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/lidarr/"))
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
        api = LidarrLibraryApis.{factory_name}(retrofit)
    }}

    @After
    fun tearDown() {{
        server.shutdown()
    }}

    private fun fixture(path: String): String = checkNotNull(
        javaClass.getResourceAsStream("/fixtures/lidarr/$path")
    ).bufferedReader().use {{ it.readText() }}

'''
    tests += '    @Test\n    fun httpErrorIsPreserved() = runTest {\n'
    tests += '        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))\n'
    tests += f'        try {{\n            api.{first[3]}(' + ', '.join(args) + ')\n'
    tests += '''            fail("Expected HttpException")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }
'''
    write(TEST / f'{tag}LibraryApiTest.kt', test_header + tests + '}')

factory = f'package {PACKAGE}\n\nimport retrofit2.Retrofit\n\n'
factory += '/** Creates the library tag clients using the connection layer\'s Retrofit. */\n'
factory += 'object LidarrLibraryApis {\n'
for tag in sorted(groups):
    name = tag[0].lower() + tag[1:]
    factory += f'    fun {name}(retrofit: Retrofit): {tag}Api =\n'
    factory += f'        retrofit.create({tag}Api::class.java)\n\n'
write(MAIN / 'LidarrLibraryApis.kt', factory + '}')
print(f'Generated {len(OPERATIONS)} operations, {len(seen)} schemas, {len(groups)} tag suites.')
