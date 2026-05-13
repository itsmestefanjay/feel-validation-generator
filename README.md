# FEEL Validation Generator

Maven plugin that reads an OpenAPI 3.x document and emits FEEL validation expressions for Camunda webhook connectors (Inbound and Intermediate). Drop the output into the connector's `activationCondition` or `responseExpression` field to keep payload validation aligned with your API contract.

Java 21, no DI framework, three runtime dependencies (`swagger-parser`, `jackson-databind`, slf4j is test-only).

## Modules

- `plugin/` — the Maven plugin (`maven-plugin` packaging).
- `example/` — a pom-only consumer; `mvn -pl example -am package` writes
  [example/target/generated-feel/customer-validation.feel](example/) for the
  [bundled sample spec](example/src/main/resources/openapi/example-api.json).

## Usage

```xml
<plugin>
  <groupId>com.consid.automation.camunda</groupId>
  <artifactId>feel-validation-generator</artifactId>
  <version>1.0.0</version>
  <executions>
    <execution>
      <goals><goal>generate-feel</goal></goals>
      <configuration>
        <openApiSpec>${project.basedir}/src/main/resources/openapi.yaml</openApiSpec>
        <outputFile>${project.build.directory}/feel/validation.feel</outputFile>
        <addResponse>false</addResponse>
      </configuration>
    </execution>
  </executions>
</plugin>
```

CLI form:

```bash
mvn com.consid.automation.camunda:feel-validation-generator:1.0.0:generate-feel \
  -DfeelValidationGenerator.openApiSpec=src/main/resources/openapi.yaml \
  -DfeelValidationGenerator.outputFile=target/validation.feel
```

## Configuration

| Parameter | Property | Default | Notes |
|---|---|---|---|
| `openApiSpec` | `feelValidationGenerator.openApiSpec` | — | Required. Path to the OpenAPI 3.x document (JSON or YAML). |
| `outputFile` | `feelValidationGenerator.outputFile` | — | Required. FEEL output destination; parent dirs are created. |
| `addResponse` | `feelValidationGenerator.addResponse` | `false` | `true` emits a response expression, `false` an activation condition. |
| `successStatusCode` | `feelValidationGenerator.successStatusCode` | `201` | HTTP status used in response mode when validation passes. |
| `failStatusCode` | `feelValidationGenerator.failStatusCode` | `400` | HTTP status used in response mode when validation fails. |
| `methods` | `feelValidationGenerator.methods` | `POST,PUT,PATCH` | Comma-separated HTTP methods to scan. |
| `mediaType` | `feelValidationGenerator.mediaType` | `application/json` | Request body media type to read schemas from. |

Status codes must be in 100–599 or `build()` throws. Path parameters are validated at `build()`; absent required parameters fail fast.

## Programmatic use

```java
FEELValidationGenerator.builder()
    .withOpenApiPath(Path.of("src/main/resources/api.yaml"))
    .withOutputFilePath(Path.of("target/validation.feel"))
    .withResponse(true)
    .withSuccessStatusCode(202)
    .withFailStatusCode(422)
    .withHttpMethods(List.of("POST"))
    .withMediaType("application/json")
    .build()
    .generate();
```

## OpenAPI support

Each required field in the resolved schema turns into one FEEL rule. The violation expression is `field=null or <type-violation>` by default; modifiers and conditionals adjust this shape.

### Data types

| OpenAPI type / format | FEEL check |
|---|---|
| `type: string` | `not(X instance of string) or is blank(X)` |
| `type: string, format: date` | `date(X)=null` |
| `type: string, format: date-time` | `date and time(X)=null` |
| `type: string, format: time` | `time(X)=null` |
| `type: number` / `type: integer` | `not(X instance of number)` |
| `type: boolean` | `not(X instance of boolean)` |
| `type: array` | `not(X instance of list) or is empty(X)` |
| `type: object` | `not(X instance of context)` |
| unrecognised / missing | `null`-check only |

Two modifiers layer on top of the type check:

- **`enum`** — appends `or not(X in (…))` to the violation. Strings are quoted, numbers and booleans emitted bare, `null` is `null`.
- **`nullable: true`** (OpenAPI 3.0) / **`type: [<t>, "null"]`** (OpenAPI 3.1) — flips the rule from `field=null or (…)` to `field!=null and (…)`, so a missing value is fine but a malformed one still fails.

### References & composition

- **`$ref`** — resolved transparently against `#/components/schemas/*`. A component referenced from multiple paths is expanded at every reference. A `$ref` that can't be resolved fails the build (no `UNKNOWN` rule fallback).
- **`allOf`** — every branch's `required` list is merged into the parent. Use for "this schema is everything in A plus everything in B".
- **`oneOf` / `anyOf`** — currently union-merged: required fields from every branch are accumulated. See *Restrictions* below.

### Conditional requirements

The basic `required: [<field>, …]` list at a given schema level produces unconditional rules. On top of that, two JSON Schema keywords let you scope a requirement to a runtime condition.

**`dependentRequired`** — *"if this field is present, those fields are also required."*

```yaml
properties:
  shippingAddress: { type: object }
  shippingCarrier: { type: string }
dependentRequired:
  shippingAddress: [shippingCarrier]
```

```feel
{invalid: req.shippingAddress!=null and (
  req.shippingCarrier=null
  or not(req.shippingCarrier instance of string)
  or is blank(req.shippingCarrier))}
```

**`if`/`then`** *(value-conditional)* — *"if this field equals a specific value, those fields are required."* Supported subset: a single-property predicate using `const` or `enum`, with `required: [<that property>]` inside the `if`. Anything outside the subset is silently skipped.

```yaml
properties:
  paymentMethod: { type: string, enum: [card, invoice] }
  cardNumber: { type: string }
if:
  properties:
    paymentMethod: { const: card }
  required: [paymentMethod]
then:
  required: [cardNumber]
```

```feel
{invalid: req.paymentMethod="card" and (
  req.cardNumber=null
  or not(req.cardNumber instance of string)
  or is blank(req.cardNumber))}
```

`enum` predicates produce an `in (…)` check, e.g. `req.tier in ("gold", "platinum") and (…)`.

**Combining triggers.** Multiple triggers for the same field — whether they come from `dependentRequired`, `if`/`then`, or both — OR-merge: `(req.a!=null or req.b="value") and (…)`. A field also listed in the unconditional `required` keeps the stricter unconditional rule.

**Inheritance into nested objects.** When a nested object is conditionally required, its own required fields inherit the parent's triggers, so the inner rules only fire when the parent's condition holds. When a nested object is **not** required at all (plain-optional), its inner required fields are omitted entirely.

### Restrictions

Known limitations of the generator. Specs may use these constructs, but they won't be honored in the emitted FEEL:

- **`if`/`then` predicates** beyond a single-property `const` or `enum` are skipped. No multi-property `if`, no nested logic, no `pattern` / range / length predicates, no `else` branch.
- **`if`/`then` dependents must be sibling property names.** Dot-paths like `card.number` in `then.required` are not honored. Place the `if`/`then` inside the nested object's schema instead — the extractor recurses, so a nested-level `if`/`then` works correctly for its own properties.
- **`oneOf` / `anyOf` are union-merged** rather than exclusive. Every branch's required fields are added, so the generated FEEL is stricter than the spec implies. Use `if`/`then` if you need exclusive alternatives.
- **No value constraints beyond `enum`.** `pattern`, `minLength` / `maxLength`, `minimum` / `maximum`, `minItems` / `maxItems`, `uniqueItems`, `multipleOf`, `additionalProperties: false`, and `const` outside `if` are not enforced.

## Output modes

### Activation condition (`addResponse=false`)

Boolean expression intended for the connector's `activationCondition` field. Invalid payloads never start a process instance; the caller receives the connector's static fallback (`statusCode: 422`, `body: { message: "activation condition not met" }`).

```feel
# POST /customers
{
  req: request.body,
  rules: [
    {invalid: req.annualIncome=null or not(req.annualIncome instance of number)},
    {invalid: req.customerId=null or not(req.customerId instance of string) or is blank(req.customerId)},
    {invalid: req.firstName=null or not(req.firstName instance of string) or is blank(req.firstName)},
    {invalid: req.newsletterConsent=null or not(req.newsletterConsent instance of boolean)},
    {invalid: req.profile=null or not(req.profile instance of context)},
    {invalid: req.profile.bio=null or not(req.profile.bio instance of string) or is blank(req.profile.bio)}
  ],
  isValid: count(rules[invalid=true])=0
}.isValid
```

![activation condition example](activationCondition.png)

### Response expression (`addResponse=true`)

Context expression intended for the connector's `responseExpression` field. The webhook **always** starts a process instance; the FEEL only shapes the HTTP response body and status. If you want invalid payloads to halt the BPMN, add a script task that re-validates and terminates.

```feel
# POST /customers
{
  req: request.body,
  rules: [
    { id: "annualIncome-invalid", field: "annualIncome", invalid: req.annualIncome=null or not(req.annualIncome instance of number) },
    { id: "customerId-invalid", field: "customerId", invalid: req.customerId=null or not(req.customerId instance of string) or is blank(req.customerId) },
    { id: "firstName-invalid", field: "firstName", invalid: req.firstName=null or not(req.firstName instance of string) or is blank(req.firstName) },
    { id: "newsletterConsent-invalid", field: "newsletterConsent", invalid: req.newsletterConsent=null or not(req.newsletterConsent instance of boolean) },
    { id: "profile-invalid", field: "profile", invalid: req.profile=null or not(req.profile instance of context) },
    { id: "profile.bio-invalid", field: "profile.bio", invalid: req.profile.bio=null or not(req.profile.bio instance of string) or is blank(req.profile.bio) }
  ],
  isValid: count(rules[invalid=true])=0,
  body: {
    message: if isValid then "Process successfully started." else "Process creation failed.",
    processInstanceKey: if isValid then correlation.processInstanceKey else null,
    details: rules[invalid=true]
  }, statusCode: if isValid then 201 else 400
}
```

![response expression example](responseExpression.png)

## Build

```bash
mvn -pl plugin verify          # tests + 80% line-coverage gate
mvn -pl example -am package    # build plugin and run it against the bundled spec
```
