These synthetic fixtures follow the Lidarr OpenAPI schemas. The specification contains no
operation examples for these tags. String placeholders use `string`, numbers and enum values
exercise the declared schema types, and dates use fixed RFC 3339 strings. No live data is used.
DELETE and action POST operations have no response content in the specification. Tests serve
empty response bodies for those operations. HEAD ping also has no HTTP response body.
