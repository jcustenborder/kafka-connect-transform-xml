# Introduction

This project converts XML stored as Kafka Connect `STRING` or `BYTES` values into
typed Kafka Connect `Struct`s using a configured XSD. Both record keys and values
are supported; `BYTES` input may be a byte array or a `ByteBuffer`.

The transform interprets the XSD once during configuration using XSOM, then uses
the JDK XML parser to validate **every document** against that schema before
mapping its elements and attributes to Connect values. It does not generate Java
source, compile classes, or use JAXB unmarshalling.

Requires **Java 17 or newer**. The build targets **Apache Kafka 4.3.1**. A runtime
without Java compiler modules is supported; conversion does not need writable
temporary storage. Building the project requires a JDK and Maven 3.9.2 or newer.

Use it in conjunction with a Source connector that reads XML data, such as from a [HTTP REST endpoint](https://www.confluent.io/hub/castorm/kafka-connect-http).

# Transformations

## FromXML(Key)

Converts XML in the record key into a typed Connect Struct based on the configured XSD.

### Configuration

| Name         | Type   | Importance | Default Value | Validator | Documentation                                                   |
| ------------ | ------ | ---------- | ------------- | --------- | --------------------------------------------------------------- |
| schema.path  | List   | High       | Empty list    | Valid URLs | XSD URLs (file, http, https); use exactly one of schema.path or schema.content |
| schema.content | String | High | Empty string | | Inline XSD document; use exactly one of schema.path or schema.content |
| package | String | High | com.github.jcustenborder.kafka.connect.transform.xml.model | Non-empty | Connect schema-name prefix; no Java package is compiled |

#### Standalone Example

```properties
transforms=xml_key
transforms.xml_key.type=com.github.jcustenborder.kafka.connect.transform.xml.FromXml$Key
# The following values must be configured.
transforms.xml_key.schema.path = http://web.address/my.xsd
```

#### Distributed Example

```json
"transforms": "xml_key",
"transforms.xml_key.type": "com.github.jcustenborder.kafka.connect.transform.xml.FromXml$Key",
"transforms.xml_key.schema.path": "http://web.address/my.xsd"
```

#### Inline Schema Example

Set the XSD directly in the connector configuration when a schema file or URL
is unavailable. Omit `schema.path` when using `schema.content`.

```json
{
  "transforms": "xml_key",
  "transforms.xml_key.type": "com.github.jcustenborder.kafka.connect.transform.xml.FromXml$Key",
  "transforms.xml_key.schema.content": "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'><xs:element name='message'><xs:complexType><xs:sequence><xs:element name='text' type='xs:string'/></xs:sequence></xs:complexType></xs:element></xs:schema>"
}
```

## FromXML(Value)

Converts XML in the record value into a typed Connect Struct based on the configured XSD.

### Configuration

| Name         | Type   | Importance | Default Value | Validator | Documentation                                                   |
| ------------ | ------ | ---------- | ------------- | --------- | --------------------------------------------------------------- |
| schema.path  | List   | High       | Empty list    | Valid URLs | XSD URLs (file, http, https); use exactly one of schema.path or schema.content |
| schema.content | String | High | Empty string | | Inline XSD document; use exactly one of schema.path or schema.content |
| package | String | High | com.github.jcustenborder.kafka.connect.transform.xml.model | Non-empty | Connect schema-name prefix; no Java package is compiled |


#### Standalone Example

```properties
transforms=xml_value
transforms.xml_value.type=com.github.jcustenborder.kafka.connect.transform.xml.FromXml$Value
# The following values must be configured.
transforms.xml_value.schema.path = http://web.address/my.xsd
```

#### Distributed Example

```json
"transforms": "xml_value",
"transforms.xml_value.type": "com.github.jcustenborder.kafka.connect.transform.xml.FromXml$Value",
"transforms.xml_value.schema.path": "< Configure me >"
```

#### Inline Schema Example

```json
{
  "transforms": "xml_value",
  "transforms.xml_value.type": "com.github.jcustenborder.kafka.connect.transform.xml.FromXml$Value",
  "transforms.xml_value.schema.content": "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'><xs:element name='message'><xs:complexType><xs:sequence><xs:element name='text' type='xs:string'/></xs:sequence></xs:complexType></xs:element></xs:schema>"
}
```

The same inline schema can be supplied in a standalone `.properties` file:

```properties
transforms=xml_value
transforms.xml_value.type=com.github.jcustenborder.kafka.connect.transform.xml.FromXml$Value
transforms.xml_value.schema.content=<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'><xs:element name='message'><xs:complexType><xs:sequence><xs:element name='text' type='xs:string'/></xs:sequence></xs:complexType></xs:element></xs:schema>
```

For these examples, `<message><text>Hello</text></message>` becomes a Struct
with a `text` string field. Exactly one of `schema.path` or `schema.content` must
be populated; configuring both or neither fails during configuration.
Whitespace-only `schema.content` is treated as absent. In JSON, escape embedded
double quotes and newlines as `\"` and `\n` respectively.

## XSD to Connect mapping

The document root must have a complex type so the result is a Connect Struct.
Supported structures include named and anonymous complex types, nested structs,
attributes, sequences, `all` groups, repeated elements, element/type references,
attribute groups, and non-polymorphic complex-type extension/restriction.
`schema.path` accepts one or more `file:`, `http:`, or `https:` XSD URLs; relative
imports/includes are resolved at configuration time. Multiple configured schemas
can contribute cross-referenced declarations to the same namespace in either
configuration order. An import without `schemaLocation` is resolved from the
explicitly configured XSD for that namespace; it cannot trigger an implicit fetch.

`schema.content` supplies one XSD directly as a string and is parsed entirely in
memory, without reading or writing a schema file. Inline imports/includes must
use absolute `file:`, `http:`, or `https:` URLs; relative `schemaLocation` values
are rejected because inline content has no base URL. For filesystem-free
environments, use a self-contained XSD or absolute HTTP/HTTPS references. Schemas
loaded through those URLs can still resolve their own relative imports/includes.

| XSD type | Connect type / Java value |
| --- | --- |
| `string`, `normalizedString`, `token`, `language`, `Name`, `NCName`, `NMTOKEN`, `ID`, `IDREF`, `anyURI` | `STRING` / `String`, with XSD whitespace normalization |
| `boolean` | `BOOLEAN` / `Boolean`; XML `true`, `false`, `1`, `0` are supported |
| `byte` | `INT8` / `Byte` |
| `short`, `unsignedByte` | `INT16` / `Short` |
| `int`, `unsignedShort` | `INT32` / `Integer` |
| `long`, `unsignedInt`, `unsignedLong`, `integer` and its sign-restricted forms | `INT64` / `Long`; values outside the signed 64-bit range are rejected |
| `float`, `double` | `FLOAT32` / `Float`, `FLOAT64` / `Double` |
| `decimal` restricted with `fractionDigits` | Connect logical `Decimal` / `BigDecimal`, using that fixed scale |
| `date` | Connect logical `Date` / `java.util.Date`, calendar date at midnight UTC; epoch days must fit INT32 |
| `time` | Connect logical `Time` / `java.util.Date`, UTC milliseconds within a day |
| `dateTime` | Connect logical `Timestamp` / `java.util.Date`, UTC instant |
| `base64Binary`, `hexBinary` | `BYTES` / byte array |
| Complex type | `STRUCT` / `Struct` |
| Repeated element | `ARRAY` / ordered `List` of the element's mapped type |

Named scalar restrictions, including patterns, enumerations, bounds and length
facets, retain the underlying Connect type and are validated for every document.
Time/timestamp values without a timezone use UTC. Sub-millisecond time/timestamp
precision that cannot be represented exactly is rejected rather than truncated.
XML Schema 1.0 BCE years are translated to ISO/proleptic Gregorian years (XML
`-0001` is ISO year `0000`), without changing the represented date or instant.

Fields use XML local names, not namespace prefixes. Named complex types use the
configured `package` prefix plus the type's class-style name; anonymous types use
their element path. Ambiguous field/schema names are rejected. Existing optional
Struct/array and primitive numeric/boolean field schemas are retained for output
compatibility. **Requiredness in the XML is still enforced by the XSD**, regardless
of output-schema optionality. Missing optional values and explicit `xsi:nil` map
to null; empty strings remain empty strings. XSD defaults/fixed values are applied
by validation when applicable.

Choice groups, repeated model groups, recursive types, wildcards, mixed/simple
content, abstract types/elements, substitution groups, list/union scalar types,
decimals without a fixed scale, and unmapped built-in scalars such as `QName` or
`duration` are rejected during configuration. Dynamic `xsi:type` is rejected when
processing a record instead of silently discarding derived fields.

Malformed XML, XSD violations, numeric overflow and conversion errors raise
`DataException`; no partial output is returned. DTDs/external entities are
prohibited, and document schema-location hints cannot load additional schemas.
Null selected-side values pass through with their existing schemas. Headers,
record metadata and the unselected key/value are preserved.

## Building and testing

```sh
mvn clean verify
```

The tests assert STRING/BYTES-to-Struct values and native Connect schemas,
including public Apache-derived fixtures with source/license attribution. Failsafe
also unpacks the plugin ZIP and launches a separate JVM with only `java.base`,
`java.xml` and `java.logging`, worker-provided Kafka/SLF4J, and the bundled plugin
dependencies. It verifies key/value conversion using URL and inline schemas, and
service discovery with compiler modules absent and `java.io.tmpdir` pointing to
a regular file.

## GitHub Actions

The Maven workflow verifies every PR, regardless of its target branch, using
Temurin 17 and `mvn -B clean verify`.
Pushes to `master` build and verify a release, then publish its Confluent Hub ZIP
as a GitHub release. The POM stays at `major.minor-SNAPSHOT`; release versions use
the next numeric patch tag in that series (for example, `0.2.0`, then `0.2.1`).
Release jobs are serialized to prevent competing patch-version calculations.
