# Public invoice structural fixture

## Source and license

The schema is adapted from Apache CXF at commit
`ba77b1da77a4461d32f93269efebb6be2e3d5aec`:

- Schema: https://github.com/apache/cxf/blob/ba77b1da77a4461d32f93269efebb6be2e3d5aec/testutils/src/main/resources/wsdl/Invoice.xsd
- Repository license: https://github.com/apache/cxf/blob/ba77b1da77a4461d32f93269efebb6be2e3d5aec/LICENSE
- Distribution notice: https://github.com/apache/cxf/blob/ba77b1da77a4461d32f93269efebb6be2e3d5aec/distribution/src/main/appended-resources/META-INF/NOTICE

The repository LICENSE and the schema's own ASF header both specify Apache
License 2.0. The schema has no additional license or copyright notice; its
`Created by IONA Artix Designer` attribution and ASF header are retained.
The full Apache-2.0 text is included in this project's root `LICENSE`.
The local `NOTICE` records relevant attribution; upstream distribution notices
for W3C/WS-* schemas and unrelated bundled libraries/code do not pertain to this
standalone courseware schema and are not reproduced.

`invoice.xml` is newly authored for this project under Apache-2.0, not an
upstream XML example. No real customer information is included.

## Modifications

- Retained the namespace, Invoice -> InvoiceHeader -> Customer nesting, and
  required repeated InvoiceLine sequence with productCode, description, quantity.
- Added the global `invoice` element so the schema can consume a document.
- Removed shipping/payment dates, monetary doubles, Address, nullable contact
  fields, and SupportLevel enumeration. Flattened Person/Customer inheritance
  into Customer's firstName, lastName and customerNumber fields.
- Added required string attributes `Invoice.id` and `InvoiceLine.warehouse`.
- Reformatted with an explicit `xs` prefix and removed unused SOAP/WSDL namespace
  declarations. Preserved unqualified local elements and attributes.
- Omitted redundant `minOccurs="1" maxOccurs="1"` defaults.

There are no imports/includes, dates, decimals, choices, external resources, or
additional dependencies. Only one schema/sample pair is included.

## Behavioral coverage

`PublicDocumentTest` exercises real `FromXml.Value<SinkRecord>` instances with
`schema.path` pointing to this local schema and a dedicated `package` prefix.
Each scenario runs with STRING and BYTES input:

1. Nested STRUCT schemas/values and root attribute: invoice `INV-1007`, customer
   Alice Martin, customer number `C-0042`.
2. ARRAY of STRUCT lines in document order (501 then 204, intentionally unsorted),
   INT32 product codes/quantities (3 then 12), STRING descriptions and warehouse
   attributes (EAST then WEST), including decoded `&amp;` -> `&`.
3. Missing the first required description must raise Connect `DataException`.
   The invalid document is derived in the test by removing that one element.

The output contract is a Connect STRUCT containing nested STRUCTs, an
ARRAY<STRUCT> of lines, STRING fields/attributes, and INT32 fields represented
by Java Integer values. Positive tests assert field counts, concrete types,
names, values, and ordering, and call Kafka Connect's recursive
`ConnectSchema.validateValue` on the actual output. XML validity alone does not
satisfy these tests; returning raw XML or JAXB/DOM objects does not satisfy them.

Expectations are hand-derived. All six cases pass with the direct XSD mapping
engine on Java 17, establishing actual Connect conversion and missing-description
rejection for both input forms. Before the engine replacement, all six errored
during XJC configuration. Historical and current results are recorded in
`docs/testing-baseline.md` at the repository root.

Independent Java 17 JAXP validation accepts the sample and rejects the same
missing-description mutation with `cvc-complex-type.2.4.a` (quantity encountered
where description is required). This establishes fixture validity separately
from transform behavior.
