# Scalar restrictions: Apache WS XmlSchema

## Source and license

Adapted from Apache WS XmlSchema at immutable commit
`94e6492b496e37f1efb5add97756caacb3dacf66`:

- [Original facets.xsd](https://github.com/apache/ws-xmlschema/blob/94e6492b496e37f1efb5add97756caacb3dacf66/xmlschema-core/src/test/resources/facets.xsd)
- [Project POM declaring Apache-2.0](https://github.com/apache/ws-xmlschema/blob/94e6492b496e37f1efb5add97756caacb3dacf66/pom.xml)
- [Core appended NOTICE](https://github.com/apache/ws-xmlschema/blob/94e6492b496e37f1efb5add97756caacb3dacf66/xmlschema-core/src/main/appended-resources/META-INF/NOTICE)
- [Distribution appended NOTICE](https://github.com/apache/ws-xmlschema/blob/94e6492b496e37f1efb5add97756caacb3dacf66/src/main/appended-resources/META-INF/NOTICE)

The source file itself carries the ASF Apache-2.0 header, retained verbatim
in `restrictions.xsd`. The complete Apache-2.0 license is distributed in the
repository root `LICENSE`. `NOTICE` retains upstream attribution, including
the appended core/distribution notices for completeness. This fixture does
not copy the separately licensed `w3c-testcases` collection. No additional
file-specific notice appears in the original `facets.xsd`.

## Deliberate adaptation

Kept only `zipCode` (string, length 5, digit pattern) and `distance` (inclusive
0..100, fixed maximum). Changed the latter's base from `xs:integer` to
`xs:int` to give an unambiguous Connect INT32 mapping. Added one `measurement`
complex root containing the original `myZipCode` and `myDistance` names in
sequence, each exactly once; qualified local elements. Retained the original
namespace. Other facets, global scalar roots, and decimal types are omitted.
The XSD includes a prominent modification notice.

All XML files are newly authored Apache-2.0 test inputs, not upstream examples.
Expected values are hand-derived: `02139` stays STRING (including its leading
zero), and `42` becomes INT32. No enum/decimal Java binding is required.
The root must be a concrete Connect Struct with exactly those two fields;
`ConnectSchema.validateValue` checks the real output against its schema.
Restrictions constrain accepted XML values, not the set of Connect output types.

## Behavioral contract

`PublicRestrictionTest` uses the real `FromXml.Value` with this schema's URL.
It checks STRING and BYTES input, then uses each configured instance for a
valid document, an invalid document, and another valid document. Validation
is mandatory for **every document**, including after the first successful
conversion; it must never be bypassed merely because binding succeeds.

| Input | XSD result |
| --- | --- |
| `valid.xml` | valid: zip `02139`, distance `42` |
| `invalid-pattern.xml` | reject five nondigits |
| `invalid-length.xml` | reject four digits |
| `invalid-min.xml` | reject -1, below inclusive minimum 0 |
| `invalid-max.xml` | reject 101, above inclusive maximum 100 |
| `invalid-required.xml` | reject absent required numeric element |
| `invalid-cardinality.xml` | reject repeated single-valued numeric element |

The invalid conversions must throw Kafka Connect `DataException`. These are
replacement-engine contract tests. On Java 17 all fourteen cases pass with the
direct XSD mapping engine, including successful conversion after rejecting a bad
record. Before migration, both valid conversions passed but all twelve invalid
cases failed because JAXB accepted invalid documents without `DataException`.
No assertions were relaxed to match silent coercion/defaulting. Independent Java 17
JAXP validation accepts the valid fixture and rejects all six invalid fixtures
without JAXB or added dependencies. Results are recorded in
`docs/testing-baseline.md` at the repository root.
