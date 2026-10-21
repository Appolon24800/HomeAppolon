These synthetic fixtures follow the Lidarr OpenAPI schemas. The specification contains no
operation examples for these tags. String placeholders use `string`, numbers and enum values
exercise the declared schema types, and dates use fixed RFC 3339 strings. No live data is used.
DELETE, reorder and bulk quality-definition updates have no response content in the
specification, so tests serve empty response bodies for those operations. The custom format
schema response has no declared content type either; its fixture mirrors the JSON array the
server returns and the client treats it as an untyped JsonElement passthrough.
