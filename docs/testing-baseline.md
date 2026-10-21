# XML transform test baseline

Recorded on 2026-10-20 against production code at `2db7325`, before changing
the parent, dependencies, service registration, or conversion engine.

## Reproduction

The inherited parent uses `http://packages.confluent.io/maven/`. Maven 3.9.16
blocks this repository before any tests execute. To test the existing code
without changing its POM, use a temporary Maven settings file:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <mirrors>
    <mirror>
      <id>confluent-https</id>
      <mirrorOf>confluent</mirrorOf>
      <url>https://packages.confluent.io/maven/</url>
    </mirror>
  </mirrors>
</settings>
```

Run with each JDK, setting `JAVA_HOME` to its installation directory:

```sh
JAVA_HOME=/path/to/jdk8 mvn -s /path/to/settings.xml clean test
JAVA_HOME=/path/to/jdk17 mvn -s /path/to/settings.xml clean test
```

The recorded environments were AdoptOpenJDK 8u292 and Temurin 17.0.1 on macOS,
with Maven 3.9.16. The existing dependency version ranges remain unchanged;
future resolution of these ranges can change the baseline.

## Results

Counts include parameterized invocations and inherited documentation tests.
Failures mean assertions failed; errors mean execution threw unexpectedly.

| Suite | Java | Run | Passed | Failures | Errors | Skipped |
|---|---|---:|---:|---:|---:|---:|
| Original tests | 8 | 5 | 5 | 0 | 0 | 0 |
| Original tests | 17 | 5 | 3 | 0 | 2 | 0 |
| Expanded tests | 8 | 45 | 34 | 6 | 5 | 0 |
| Expanded tests | 17 | 45 | 16 | 5 | 24 | 0 |

No production changes or test exclusions were made to obtain these results.
The expanded suite deliberately remains red to expose defects and the missing
service-loader feature before implementation. It contains both characterization
tests for existing output and regression tests for required corrections.

## Failures to address

| Test / scenario | Observed behavior | Expected behavior |
|---|---|---|
| Original `FromXmlTest.apply` and documentation example on Java 17 | Nested books schema fails in `KafkaConnectPlugin.type` with `ConcurrentModificationException` from `HashMap.computeIfAbsent`, surfaced as `ConnectException: Schema compiler could not bind schema.` All 22 expanded books-based invocations are blocked here too. | Configure and convert successfully on Java 17. |
| `ServiceLoaderTest.discoversAndInstantiatesKeyAndValueTransforms` | Neither transform is discovered. | Discover and instantiate both concrete providers, excluding the abstract base. |
| `FromXmlTest.passesThroughNullSelectedSide` (schemaful key and value) | `NullPointerException` while unmarshalling a null string. Schemaless nulls pass. | Preserve null and its optional schema; preserve the opposite side. |
| `FromXmlTest.honorsDeclaredByteEncoding` | Valid UTF-16 XML is decoded by `InputStreamReader` using the default charset, then fails parsing. | Honor the XML byte encoding and preserve non-ASCII text. |
| `FromXmlTest.rejectsInvalidNumericValue` | `not-a-number` is silently converted to `0.0`. | Throw `DataException`, without manufacturing data. |
| `FromXmlTest.dateValuesUseConnectUtcRepresentation` | Under `America/Los_Angeles`, dates become local midnight. `Date.fromLogical` rejects them because they are not UTC day boundaries. | Produce a valid Connect logical Date independent of the host time zone. |
| `ScalarMappingTest.mapsExplicitNilToNull` | Optional/nillable text creates `JAXBElement<String>`, which the XJC plugin rejects with `UnsupportedTypeException`; configuration fails on both JDKs. | Configure successfully and map `xsi:nil` to an optional null. |
| `ScalarMappingTest.rejectsNumericOverflow` (four cases) | Byte `128`, short `32768`, and int `2147483648` wrap negative; long `9223372036854775808` becomes null. | Throw `DataException` on out-of-range values. |

On Java 17, scalar cases without nillable elements do reach conversion. Their
four overflow failures remain observable separately from the books schema's
configuration failure.

## Passing behavior now protected

- The original books byte-input example now asserts the output instead of
  discarding it.
- Both transforms process strings and bytes, with and without input schemas
  (eight combinations on Java 8).
- The selected side changes while the opposite schema/value, topic, partition,
  offset, timestamp, timestamp type, headers, and original input are preserved.
- Books output uses `BooksForm`/`BookForm` schema names under the existing model
  package. Field names, order, types, optionality, two array entries, attributes,
  and non-date scalar values are asserted.
- Existing schema quirks are characterized: root/nested structs and the `book`
  array are optional, and required XSD `price` is an optional Connect float.
  Date semantics are tested separately so the date defect does not obscure
  these compatibility checks.
- String, boolean (including XML `0`), byte, short, int, long, float, and double
  mappings assert their actual Connect types and values, including valid integer
  boundaries, XML escaping, and Unicode text.
- Missing optional text differs from empty text; absent optional numbers and
  attributes remain null.
- Empty/malformed XML, unknown roots/namespaces, missing required string fields,
  and an external entity pointing to a real local fixture are rejected.
- Missing schema configuration and malformed schema URLs raise `ConfigException`.

## Remaining migration verification

This suite is a starting boundary contract, not comprehensive XSD coverage.
Before implementing additional XSD features, define and test their mapping:
imports/includes, anonymous types, element references, qualified attributes,
choice/all groups, inheritance, restrictions/defaults, decimals, time/timestamp,
binary types, wildcard/mixed content, and recursive types. Unsupported constructs
must fail explicitly rather than silently lose data.

The replacement must also be tested using a packaged plugin in a Java 17 runtime
without `jdk.compiler`, with no generated-source/class output requirement. Maven
unit tests running inside a JDK do not establish compiler-free deployment.

Retest dependency resolution without the temporary settings file after the parent
upgrade, and verify the packaged service descriptor and plugin dependencies.

## Parent upgrade checkpoint: 4.3.1-2

Updated only the parent version in `pom.xml` and ran on Temurin 17.0.1:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home mvn clean test
```

The build resolves dependencies and reaches tests without the temporary Maven
settings file or HTTP-blocker errors. The parent specifies Kafka 4.3.1, Java 17,
connect-utils 0.8.0, JUnit 6.1.3, and Surefire 3.6.0.

The expanded suite still reports **45 tests: 16 passed, 5 failures, 24 errors,
0 skipped**. The books schema's XJC `ConcurrentModificationException`, unsupported
nillable fields, numeric overflow, and missing service providers remain as
documented above. The parent upgrade does not replace the XML conversion engine.

There is also a non-blocking metadata warning from a legacy dependency repository,
`https://maven.java.net/content/repositories/releases/`, whose TLS certificate
fails validity checks under this JDK. Dependency resolution nevertheless completes;
no certificate checks were disabled.

## Public fixture checkpoint

Added three compact, Apache-2.0 fixture sets under
`src/test/resources/com/github/jcustenborder/kafka/connect/transform/xml/public/`.
Schemas are adapted from pinned public Apache CXF and Apache XMLSchema sources;
XML documents are authored test inputs, not customer data. Each folder records
immutable source URLs, modifications, and attribution/notices in its README and
NOTICE files. No external downloads are required when running the tests.

| Fixture / test | Coverage | Java 17 result |
|---|---|---|
| `document/` / `PublicDocumentTest` | CXF-derived invoice, nested customer/header Structs, repeated typed lines, attributes, list order, configured schema names, required content | 3 tests, 3 configuration errors caused by the existing nested-schema XJC `ConcurrentModificationException` |
| `namespaces/` / `PublicNamespaceTest` | XMLSchema-derived root referencing an imported type and a relative include, `all` group, qualified elements/attribute, prefix independence, wrong root namespace | 3 tests passed |
| `restrictions/` / `PublicRestrictionTest` | XMLSchema-derived string pattern/length and integer range restrictions; valid STRING/BYTES conversions; rejection of six invalid documents in both input forms | 14 tests: 2 valid conversions passed; 12 assertions failed because invalid XML was accepted |

The restriction tests reuse each configured transform for valid → invalid → valid
input to require validation on every record and continued usability after a
validation failure. Currently they fail on invalid input, before the final valid
record is reached. Invalid cases exercise nondigit/short ZIP codes, distance below
0 or above 100, an omitted required numeric element, and repeated single-valued
content. The short ZIP violates both its length and digit-count pattern; this
fixture does not isolate the two facets.

Independently checked every fixture set with Java 17 JAXP `SchemaFactory` and
`Validator`: valid examples and equivalent namespace prefixes are accepted;
missing invoice description, wrong root/child namespaces, unqualified required
attribute, and all six invalid restriction examples are rejected. These checks
establish that test inputs match the XSD contract independently of the transform.

After the service-loader fix and addition of the public fixtures, ran:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home mvn clean test
```

**65 tests: 22 passed, 16 failures, 27 errors, 0 skipped.** `ServiceLoaderTest`
passes. No production conversion code was changed, no validation assertions were
weakened, and no new dependencies were added for these fixtures.

The replacement engine must preserve the working import/include and namespace
behavior while resolving the invoice configuration failures and enforcing the
restriction/requiredness/cardinality rules on every document. This corpus still
does not establish support for every XSD construct; additional focused fixtures
are needed before implementing other mappings.

## Connect mapping audit

The SMT's primary contract is STRING/BYTES XML input → typed Connect Struct
output. Independent XSD validation checks fixture correctness but does not prove
that conversion contract. Audited every public fixture against this requirement:

| Fixture | Expected Connect output | Mapping verification on Java 17 |
|---|---|---|
| Invoice | Root/nested STRUCTs, ARRAY<STRUCT> invoice lines; STRING identifiers, descriptions, names and attributes; INT32 product codes/quantities with Integer values | Assertions cover both input forms and all field counts/types/values across the positive cases, but actual mapping remains blocked during XJC configuration. No passing mapping claim is made. |
| Imported namespaces | Root STRUCT with two STRING fields (`varString`, `sourceId`); namespace prefixes are not output types or field-name prefixes | Actual STRING/BYTES conversions pass; imported type resolution and equivalent prefixes preserve the asserted schemas/values. |
| Scalar restrictions | Root STRUCT, STRING `myZipCode` preserving leading zero, INT32 `myDistance` with Integer value 42 | Actual STRING/BYTES conversions pass. Pattern/range/requiredness/cardinality rejection still fails because XML validation is missing. |

Every public positive mapping assertion now invokes Kafka Connect 4.3.1's
`ConnectSchema.validateValue(output.valueSchema(), output.value())` after requiring
a concrete Struct. This checks schema/value correspondence and recursively
validates nested Structs, arrays, and their Java value types. Explicit expected
fields/types/values remain necessary: merely producing any valid Connect value
would not establish the correct mapping. The books and scalar fixture output
assertions also use Connect's validator.

Invoice and namespace scenarios now each run with both STRING and BYTES input;
restriction scenarios already did. The public corpus is now 26 cases: 8 pass,
12 fail invalid-document rejection, and 6 invoice cases error during configuration.
The full Java 17 `mvn clean test` run reports **71 tests: 25 passed, 16 failures,
30 errors, 0 skipped**. No production code or dependencies changed during this
audit. Namespace/scalar conversion is verified; blocked invoice mapping and
mandatory XML validation remain explicit implementation work.

## Direct XSD mapping migration: verified

Replaced XJC/JAXB model generation, runtime Java compilation, generated-model
class loading, and JAXB unmarshalling with XSOM 4.0.9 schema interpretation and
the JDK's validating XML parser. The original 71-case contract remains active.
`DirectMappingTest` adds 46 cases, and `CompilerFreeRuntimeIT` adds one packaged
runtime check.

The added tests were run against the old engine before implementation: they
exposed missing validation, incorrect UTC dates/defaults, ByteBuffer casting,
unsafe/unrepresentable schema handling, and missing compiler classes when compiler
modules were unavailable. Subsequent review regressions reproduced cross-file
schema-resolution failures, logical-Date overflow, and BCE year errors before
their fixes. No pre-migration mapping assertions were relaxed or disabled.

Final verification on Temurin 17.0.1 with Maven 3.9.16:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home mvn clean verify
```

**BUILD SUCCESS: 117 unit cases plus 1 integration case, 0 failures, 0 errors,
0 skipped. Checkstyle: 0 violations.** Sources/Javadoc, the plugin JAR,
Confluent Hub ZIP and tarball packaging complete successfully.

Verified behavior includes:

- STRING/BYTES-to-Struct conversion for both keys and values, including the public
  invoice fixture that previously failed configuration. ByteBuffer contents are
  read without changing its position/limit.
- Mandatory per-document XSD validation and usable conversion after a rejected
  record. Legacy strict-check settings cannot disable validation.
- Native Connect logical Date/Time/Timestamp/Decimal, binary fields, widened
  unsigned values and checked INT64 integer mappings. Logical Date day counts
  are checked against INT32 storage bounds before encoding. XML Schema 1.0 BCE
  numbering is translated into ISO numbering for Date and Timestamp.
- Relative imports/includes, nested chameleon includes, same-namespace cross-file
  references in both configuration orders, namespace-only imports from explicitly
  configured schemas, and explicit imports combined with configured namespace
  siblings. Both XSOM and JAXP consume the complete, cached schema graph.
- Explicit rejection of unsupported mappings, dynamic `xsi:type`, DTDs/entities,
  invalid values and schema/value mismatches, rather than discarded data or
  manufactured defaults.

The integration test unpacks the actual Confluent Hub ZIP. Its child JVM uses
only bundled plugin dependencies, worker-provided Kafka API/client and SLF4J API
JARs, and the probe class. It limits modules to `java.base,java.xml,java.logging`,
checks that both compiler modules are absent, verifies the transform's code source
is the unpacked plugin JAR, discovers/instantiates both service providers, and
performs all four key/value × STRING/BYTES conversions into validated Structs.
`java.io.tmpdir` points to a regular file, preventing temporary-file generation.

Runtime dependency-tree and ZIP/tarball inspection confirm XSOM and its
relaxng-datatype support are included, XJC/JAXB API/runtime/model-generation JARs
are absent, and Kafka worker API/client JARs are not bundled. The two service
provider entries remain in the plugin JAR. Existing warnings in inherited
documentation/packaging or legacy test dependency repository metadata do not
prevent verification; no checks were bypassed.

An independent read-only code review identified the schema-composition and Date
edge cases above. Their regression tests pass, and follow-up review confirmed the
fixes with no remaining blockers. README documents the supported Connect mapping
table and explicitly rejected XSD constructs; this is not a claim to map every
possible XML Schema construct.
