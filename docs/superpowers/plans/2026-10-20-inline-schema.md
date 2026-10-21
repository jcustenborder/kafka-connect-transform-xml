# Inline Schema Implementation Plan

**Goal:** Support `schema.content` without filesystem access.

**Architecture:** Reuse the schema cache, XSOM interpreter, and JAXP validator.
Give the inline root a stable synthetic system ID and reject relative locations
on that root; preserve existing URL graph resolution.

**Tech Stack:** Java 17, Maven, JUnit Jupiter, XSOM, JAXP.

## Constraints

- Target `v2.0-SNAPSHOT`; exactly one of `schema.path`/`schema.content`.
- No new dependencies or temporary schema files.
- Preserve XML security and existing URL behavior.

## Execution

1. Add behavioral tests in `FromXmlConfigTest` and `InlineSchemaTest`: missing,
   empty, conflicting sources; both record sides; Unicode/encoding; invalid XSD;
   XSD violations; DTD/entity rejection; absolute and relative references.
   Run `mvn -B -Dtest=FromXmlConfigTest,InlineSchemaTest test` and confirm failures
   are due to missing inline support.
2. Add optional `schema.content` and empty URL default in `FromXmlConfig`.
   Enforce exclusive populated sources with `ConfigException`.
3. Extend `SchemaSources` to cache the inline root through shared metadata
   validation, expose its InputSource to `XmlSchema`, and preserve character
   semantics for config strings. Reject relative schemaLocation on inline roots.
   Update `FromXml` configuration logging to identify inline mode.
4. Run the focused tests, update README configuration tables and inline examples,
   then run Java 17 `mvn -B clean verify`.
5. Review the complete diff, commit intended files, push the feature branch,
   and create a PR with base `v2.0-SNAPSHOT` and verification evidence.
