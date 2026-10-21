# Apache XMLSchema namespace/import fixture

Upstream: **Apache XMLSchema**, `apache/ws-xmlschema`, commit
`94e6492b496e37f1efb5add97756caacb3dacf66`.

## Pinned sources and licensing

- [importBase.xsd](https://github.com/apache/ws-xmlschema/blob/94e6492b496e37f1efb5add97756caacb3dacf66/xmlschema-core/src/test/resources/importBase.xsd)
- [importAux.xsd](https://github.com/apache/ws-xmlschema/blob/94e6492b496e37f1efb5add97756caacb3dacf66/xmlschema-core/src/test/resources/importAux.xsd)
- [Repository license declaration](https://github.com/apache/ws-xmlschema/blob/94e6492b496e37f1efb5add97756caacb3dacf66/pom.xml):
  `licenses/license/name` is `Apache-2.0` and the URL is
  `https://www.apache.org/licenses/LICENSE-2.0.txt`.
- [Core NOTICE additions](https://github.com/apache/ws-xmlschema/blob/94e6492b496e37f1efb5add97756caacb3dacf66/xmlschema-core/src/main/appended-resources/META-INF/NOTICE)
- [Distribution NOTICE additions](https://github.com/apache/ws-xmlschema/blob/94e6492b496e37f1efb5add97756caacb3dacf66/src/main/appended-resources/META-INF/NOTICE)

Both source schemas carry explicit ASF Apache License 2.0 headers, retained
in the copied/derived schemas. Neither has a separate file-specific copyright
or alternative license notice. `LICENSE` contains the Apache 2.0 text.
`NOTICE` retains the upstream notice additions (including their IBM/W3C
attributions for completeness). No W3C test-suite files or implementation
code are copied here; those broader distribution notices do not relicense
these ASF-header schemas. `echo.xml` is newly authored for this project under
Apache-2.0, rather than represented as an upstream XML instance.

## Exact adaptations

- `importBase.xsd`: copied unchanged except CRLF-to-LF normalization and
  removal of an empty trailing line. Its relative import, namespaces,
  `echoStructParam` element and `xsd2:SOAPStruct` type reference are retained.
- `importAux.xsd`: replaced the inline type with a relative
  `<include schemaLocation="struct.xsd"/>`; removed unused `SOAPWrapper`
  global element and unused `wsdl`, `xsd`, `xsd1` declarations; normalized
  whitespace and added a modification notice. Target namespace and qualified
  element default are retained.
- `struct.xsd`: extracted `SOAPStruct` from upstream `importAux.xsd` with its
  `all` compositor and required `varString` string element; removed numeric
  `varFloat` and `varInt` to isolate resolution from scalar conversion;
  added required `sourceId` string attribute with `form="qualified"`.
  Added schema wrapper with the same target namespace/qualified element
  default, retained ASF header, and added a modification notice.
- `echo.xml`: supplies `varString="Hello from the imported type"` (element
  text) and `sourceId="sample-001"` (qualified attribute).

## Covered behavior and hand-derived expectations

Only `importBase.xsd` is configured in `schema.path`. Resolution must follow
`importBase.xsd -> importAux.xsd -> struct.xsd` using relative system IDs.
The root is `{http://soapinterop.org/xsd}echoStructParam`; its complex type,
child element and attribute belong to `http://soapinterop.org/xsd2`.
No document `schemaLocation` hints or explicit dependency URLs are supplied.

`PublicNamespaceTest` checks real `FromXml.Value<SinkRecord>` output:

1. Imported `SOAPStruct` becomes a Connect STRUCT with configured package
   prefix `com.github.jcustenborder.kafka.connect.transform.xml.publicmodel`.
   The two distinct XML local names produce the existing wire names
   `varString` and `sourceId`, both required Connect STRINGs with the literal
   values above. Namespace prefixes are not part of those field names.
2. Renaming both prefixes (`root`/`data` to `message`/`payload`), including
   the attribute prefix, preserves the exact values and schema.
3. Replacing only the root namespace URI with `urn:wrong:root` must throw
   `DataException` when applied. Configuration is outside `assertThrows`,
   so a configuration failure cannot accidentally satisfy this assertion.

Each scenario runs with STRING and BYTES input. Positive output checks require
a concrete Connect Struct and invoke Connect's recursive
`ConnectSchema.validateValue`, in addition to checking exact field schemas and
values. Imported types and XML namespaces do not introduce special output types:
SOAPStruct maps to STRUCT and both fields map to STRING.

All six cases pass with both the pre-migration XJC engine and the direct XSD
mapping engine on Java 17. These characterization tests protect schema resolution,
namespace handling, and Connect output across the replacement. Results are recorded in
`docs/testing-baseline.md` at the repository root.

Independent fixture validation with Eclipse Temurin **17.0.1+12**, using only
JDK `SchemaFactory`/`Validator` and the root schema file, passed five checks:
original XML accepted; equivalent-prefix XML accepted; wrong root namespace,
wrong child namespace and unqualified `sourceId` attribute each rejected with
`SAXException`. External schema access was limited to `file`, with external
DTD access disabled. These fixture checks are independent of the FromXml tests.
