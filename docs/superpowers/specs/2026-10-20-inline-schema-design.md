# Inline XSD configuration

Target: `v0.2-SNAPSHOT`.

Add `schema.content`, a string containing one XSD, as an alternative to
`schema.path`. Exactly one source must be populated; blank content counts as
absent. Preserve the existing URL-list behavior and schema-name prefix.

Cache inline content in memory under a synthetic system ID and reuse XSOM and
JAXP mapping/validation. Do not create temporary files or add dependencies.
Absolute imports/includes remain supported; relative locations in the inline
document fail explicitly because there is no base URL. Referenced URL documents
retain their own relative-resolution behavior. Preserve DTD/entity protections.

Success criteria: key and value transformations accept inline schemas, produce
the same typed output, enforce XSD constraints, and reject malformed/unsafe
schemas and conflicting/missing configuration. Existing URL tests continue to
pass. Document standalone/distributed examples and verify with Java 17 using
`mvn -B clean verify` before opening the PR against `v0.2-SNAPSHOT`.
