These synthetic fixtures follow the Lidarr OpenAPI schemas. The specification contains no
operation examples for these tags. String placeholders use `string`, numbers and enum values
exercise the declared schema types, and dates use fixed RFC 3339 strings. No live data is used.
The calendar feed is iCal text because the spec declares no response content for the .ics
endpoint. The `tags` query stays a raw string, matching the spec.
