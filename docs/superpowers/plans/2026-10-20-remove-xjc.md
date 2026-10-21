# Remove runtime XJC implementation plan

> **For agentic workers:** Execute inline using executing-plans, preserving the existing PR branch and tests.

**Goal:** Convert STRING/BYTES XML into valid Connect Structs on Java 17 without Java compilation.

**Architecture:** XSOM resolves XSD declarations into Connect schema/mapping metadata at configuration time. JAXP validates each document while building a DOM; the metadata maps that DOM into native Connect values. Schema bytes are cached during configuration so both schema consumers see identical inputs.

**Tech stack:** Java 17, Kafka Connect 4.3.1, XSOM 4.0.9, JDK XML APIs, existing JUnit tests.

## Global constraints

- Continue PR #54 against `v2.0-SNAPSHOT`; preserve existing schema names/optionality.
- No compiler API, generated model classes, JAXB unmarshalling, or temp-file requirement.
- Mandatory per-document validation; DTDs/entities and record-selected external schemas are prohibited.
- No Eclipse metadata, test exclusions, or masked errors.

## Tasks

### 1. Establish red contract tests

- [x] Confirm the existing books/restriction failures on Java 17.
- [x] Add `DirectMappingTest` for Connect logical values, binary/unsigned types, defaults, repeated scalar fields, references/inheritance, validation recovery, and explicit unsupported-schema errors. Use literal expected values and `ConnectSchema.validateValue`.
- [x] Add a Failsafe packaged-runtime check which launches Java with `--limit-modules java.base,java.xml,java.logging` and an unusable temp directory. Discover providers from the unpacked plugin ZIP and convert a document into Struct, using only bundled dependencies plus worker-provided Kafka/SLF4J.
- [x] Run the added unit tests before changing production code; record the failures.

### 2. Interpret XSD and convert records

Files: add `SchemaSources.java`, `XmlSchema.java`, `XmlType.java`; update `FromXml.java`, `FromXmlConfig.java`, `ConnectErrorListener.java`, and `pom.xml`.

Interfaces:
- `SchemaSources.source(URL)` supplies cached schema bytes/system IDs to XSOM and JAXP resolvers.
- `XmlSchema(FromXmlConfig)` creates reusable schema/mapping metadata.
- `XmlSchema.read(InputSource)` validates and returns `SchemaAndValue` containing a Struct.
- `XmlType` holds a Connect schema, scalar conversion metadata, and ordered element/attribute members.

- [x] Replace XJC/JAXB dependencies with XSOM 4.0.9; use the parent's packaging plugin version.
- [x] Secure both schema parsing and document parsing; load schema imports/includes only from configuration-time sources.
- [x] Derive named/anonymous Structs, arrays, supported scalars and logical types; reject ambiguous, recursive, wildcard, choice, mixed/simple-content and unsupported scalar mappings explicitly.
- [x] Make `FromXml` implement Transformation directly, preserving key/value metadata and null schemas and accepting strings, byte arrays and ByteBuffers.
- [x] Keep obsolete XJC config keys as documented ignored/deprecated compatibility options; they cannot disable validation.
- [x] Delete `XSDCompiler.java` and the unused test-only `ConnectPlugin.java`.
- [x] Run all unit tests and fix actual conversion failures without weakening assertions.

### 3. Verify packaging and document the result

- [x] Update README with native Connect mappings, mandatory validation, supported/rejected XSD constructs, compatibility options and Java 17 runtime requirements.
- [x] Run Java 17 `mvn clean verify`, including the packaged compiler-free check.
- [x] Inspect the runtime dependency tree and packaged archives: no XJC/JAXB runtime or bundled Kafka APIs.
- [x] Append actual results to `docs/testing-baseline.md` and update fixture status documentation.
- [x] Review implementation and resolve material code-review findings with regression tests.

Delivery target: commit verified changes on the existing branch and update PR #54
against `v2.0-SNAPSHOT` with the actual verification results.
