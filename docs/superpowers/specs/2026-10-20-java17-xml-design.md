# Java 17 XML transform migration

## Goals and approved compatibility policy

- Inherit `com.github.jcustenborder.kafka.connect:kafka-connect-parent:4.3.1-2`
  and target Java 17.
- Update project dependencies to current compatible stable versions.
- Register `FromXml$Key` and `FromXml$Value` for Kafka Connect service discovery.
- Replace runtime Java generation, compilation, and class loading with direct
  XSD-to-Connect schema and XML-to-Struct mapping.
- The primary contract is STRING/BYTES XML input → typed Connect Struct output.
  XML validation alone is not success: positive tests must assert expected native
  Connect schemas and values and validate actual output with Connect's recursive
  schema/value validator.
- Preserve existing output schema names, XML field names/order, and optionality,
  including the optional numeric-field behavior. The user selected this policy.
- Correct the conversion defects recorded in `docs/testing-baseline.md`.

## Approach

Use the maintained XSOM schema-model library, independently of JAXB/XJC, to
resolve XSD declarations, named types, imports, and includes. Build immutable
mapping metadata and Connect schemas once during transform configuration.

Use the JDK's namespace-aware XML parser and XSD validator to validate documents
and traverse their elements/attributes into Structs. Validation against the
configured XSD is mandatory for every document, during parsing; there is no
validation bypass setting. Build the reusable JAXP schema once during
configuration, with a per-parse validator/parser. Only a fully parsed and valid
document may be converted and returned. A DOM representation keeps
the implementation small; the existing implementation already materializes an
entire JAXB object graph. Streaming can be considered only if record sizes make
DOM memory usage a demonstrated problem.

The alternatives were build-time JAXB models (cannot retain arbitrary configured
XSDs) and explicit field mappings (changes the configuration model). Neither
meets the selected arbitrary-XSD interpretation approach.

## Mapping contract

- Keep `schema.path` URL lists and `package` as the schema-name prefix.
- Support named/anonymous complex types, nested elements, attributes, sequence
  and all groups, repeated elements, named type/element references, and
  namespace-qualified lookup. Resolve relative XSD includes/imports.
- Support strings, booleans, fixed-width integers, floats/doubles, dates,
  times/timestamps, binary values, and fixed-scale decimals. Derived scalar
  restrictions use the underlying supported type and XSD validation.
- Use XML names for fields. Named complex types retain their type-based Connect
  names. Anonymous types derive deterministic names from their element path.
- Preserve the tested optional struct/array and primitive numeric-field schemas.
  Validate actual requiredness against the XSD independently of Connect schema
  optionality.
- Distinguish missing optional values, empty strings, and explicit nil values.
- Fail during configuration for ambiguous field names, recursive types, mixed
  or simple content, wildcards, choice/repeated groups, abstract types/elements,
  substitution groups, list/union scalars, and unsupported scalar types. Do not
  silently drop constructs that cannot be represented by this mapping.
- Reject dynamic `xsi:type` at record processing rather than returning a base
  Struct that discards derived fields. Unbounded/sign-restricted integers retain
  their legacy INT64 mapping with exact range checks; decimals require an XSD
  `fractionDigits` scale. Date/time/timestamps use UTC, with no silent loss of
  sub-millisecond time/timestamp precision.
- Retain old XJC option names as documented deprecated compatibility settings;
  there is no XJC execution after migration.

## Runtime/error behavior

- Parse byte arrays as bytes so XML-declared encodings are honored.
- Preserve nulls and their input schemas on the selected side.
- Preserve record metadata, headers, and the unselected side.
- Reject malformed documents, invalid typed values, overflow, and XSD validation
  failures with `DataException`; invalid schema configuration fails explicitly.
- Enforce required elements/attributes, occurrence limits, namespaces, and scalar
  restrictions through XSD validation, independently of preserved Connect schema
  optionality. A validation failure rejects the record without returning partial
  output.
- Convert Connect dates to UTC day boundaries, independent of the host timezone.
- Reject DTDs and external entities in schemas and instance documents.
  Configured schema URLs and their relative imports/includes remain supported;
  record content cannot choose additional external schema locations.
- No compiler APIs, generated Java/classes, dynamic model classloader, or
  writable temporary directory are required by conversion.

## Verification

1. Original tests: five pass on Java 8; two error on Java 17. Recorded before edits.
2. Expanded pre-migration suite: 45 cases with failures documented before
   production changes. Keep these assertions active during migration.
   Three public Apache-derived fixture sets add 20 cases (65 total after service
   registration). Protect working relative imports/includes, `all` groups,
   namespace/prefix handling, qualified attributes, and restricted scalar output;
   fix nested invoice configuration and invalid-document acceptance. Sources,
   adaptations, and observed outcomes are recorded with the fixtures and in
   `docs/testing-baseline.md`.
   The Connect-mapping audit expands these to 26 public cases (71 total), with
   both STRING and BYTES inputs and `ConnectSchema.validateValue` on actual
   positive outputs. Only runtime-passing mapping tests count as verified support.
3. Add focused tests before implementing mapping constructs beyond those fixtures,
   including references/imports, namespaces, unsupported constructs, and logical
   types. Verify per-record validation of required numeric fields, required
   attributes, occurrence limits, unexpected elements, and scalar restrictions.
4. Run the full Maven verification lifecycle on Java 17, including packaging.
5. Run conversion and service discovery from packaged artifacts using Java 17
   with compiler modules unavailable and a non-writable temp directory.
6. Inspect the runtime dependency tree and package contents for removal of XJC,
   JAXB model generation, and provided Kafka libraries from plugin bundles.

Implementation PRs target `v2.0-SNAPSHOT`. The baseline report remains a
historical record; subsequent verification results are appended separately.
