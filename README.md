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

Each required field in the resolved schema turns into one FEEL rule. **Every clause the generator emits describes when the field is _invalid_** — the rule evaluates to `true` when at least one clause fires. The surrounding template wraps each rule in `{invalid: <clauses>}` and counts the `true` ones (`count(rules[invalid=true])=0`), so a rule that's `true` means the payload is rejected.

The default rule body is `field=null or <type-violation>`. Modifiers (`enum`, `nullable`), value constraints (length, range, pattern), and conditional triggers add or transform clauses while preserving the "true ⇒ invalid" reading.

Example. `age` declared as `type: integer, minimum: 18` emits:

```
req.age=null or not(req.age instance of number) or req.age<18
```

Read left to right: age is invalid if **missing** *or* **not a number** *or* **below 18**.

### Data types

The type clause fires on the value's runtime kind only. Required arrays may be empty, required strings may be `""`, etc., unless the schema declares a constraint that says otherwise (see *Value constraints* below).

| OpenAPI type / format | Violation clause (true ⇒ invalid) |
|---|---|
| `type: string` | `not(X instance of string)` |
| `type: string, format: date` | `date(X)=null` |
| `type: string, format: date-time` | `date and time(X)=null` |
| `type: string, format: time` | `time(X)=null` |
| `type: number` / `type: integer` | `not(X instance of number)` |
| `type: boolean` | `not(X instance of boolean)` |
| `type: array` | `not(X instance of list)` |
| `type: object` | `not(X instance of context)` |
| unrecognised / missing | `X=null` only (no type clause) |

Three modifiers layer on top of the type clause:

- **`enum`** — adds `or not(X in (…))` to the violation chain, so the field is also invalid when its value isn't in the allowed set. Strings are quoted, numbers and booleans emitted bare, `null` is `null`.
- **`const`** — treated as a single-value enum: `const: "v1"` is equivalent to `enum: ["v1"]`. Useful for pinning a schema version or discriminator value without writing a one-element enum.
- **`nullable: true`** (OpenAPI 3.0) / **`type: [<t>, "null"]`** (OpenAPI 3.1) — flips the rule from `field=null or (…)` to `field!=null and (…)`, so a missing value is no longer treated as invalid; only a present-but-malformed one is.

### Value constraints

Schema keywords that bound the value's contents add OR-clauses to the violation chain. Each is emitted only when the schema declares it — there's no implicit "non-empty" / "non-blank" assumption.

**Strings** (including `date`, `date-time`, `time` subtypes):

| Keyword | Violation clause (true ⇒ invalid) |
|---|---|
| `minLength: N` | `string length(X)<N` |
| `maxLength: N` | `string length(X)>N` |
| `pattern: <regex>` | `not(matches(X, "<regex>"))` |
| `format: email` | `not(matches(X, "^[^@\s]+@[^@\s]+\.[^@\s]+$"))` |
| `format: uuid` | `not(matches(X, "^[0-9a-fA-F]{8}-…-[0-9a-fA-F]{12}$"))` |
| `format: uri` / `format: url` | `not(matches(X, "^[a-zA-Z][a-zA-Z0-9+.-]*:.+$"))` |

`minLength: 0` is preserved as the explicit "may be empty" signal and emits no clause. Pattern strings are FEEL-escaped (backslashes and quotes). `format` only kicks in when no explicit `pattern` is set — author-supplied patterns always win. Format regexes are intentionally permissive — they match author intent ("looks like a UUID"), not RFC-perfect validation.

**Arrays:**

| Keyword | Violation clause (true ⇒ invalid) |
|---|---|
| `minItems: N` | `count(X)<N` |
| `maxItems: N` | `count(X)>N` |
| `items: <schema>` | `(some e in X satisfies (<element-violation>))` |

`minItems: 0` (or absence) means a required key may carry an empty list. To require non-empty, set `minItems: 1`.

The `items` clause recurses: each element is validated against its full schema, including its own required fields when the element is an object. For an array of objects `{sku, quantity}` both required, the emitted clause is:

```
(some e in X satisfies (
  e=null or not(e instance of context)
  or e.sku=null or not(e.sku instance of string)
  or e.quantity=null or not(e.quantity instance of number)))
```

Read as "X is invalid if some element `e` is missing, of the wrong shape, or has any required child missing/wrong".

**Objects:**

| Keyword | Violation clause (true ⇒ invalid) |
|---|---|
| `additionalProperties: false` | `(not(every k in get entries(X).key satisfies (k in (<declared keys>))))` |

When set on the root request schema, the generator emits an extra rule with id `rootObject-invalid` that pins the top-level payload to its declared keys. On nested objects, the clause is folded into the existing rule for that field. The schema form of `additionalProperties` (a sub-schema specifying allowed value types) is not honored — only the strict boolean-false case.

**Numbers** (both `number` and `integer`):

| Keyword | Violation clause (true ⇒ invalid) |
|---|---|
| `minimum: N` | `X<N` |
| `maximum: N` | `X>N` |
| `exclusiveMinimum: N` *(OpenAPI 3.1 number)* | `X<=N` |
| `minimum: N` + `exclusiveMinimum: true` *(OpenAPI 3.0 boolean)* | `X<=N` (normalized) |
| `exclusiveMaximum: N` *(OpenAPI 3.1 number)* | `X>=N` |
| `maximum: N` + `exclusiveMaximum: true` *(OpenAPI 3.0 boolean)* | `X>=N` (normalized) |
| `multipleOf: N` | `modulo(X, N)!=0` |

Both OpenAPI 3.0 (boolean) and OpenAPI 3.1 (numeric) forms of `exclusiveMinimum` / `exclusiveMaximum` are recognized and normalized into a single representation.

### References & composition

- **`$ref`** — resolved transparently against `#/components/schemas/*`. A component referenced from multiple paths is expanded at every reference. A `$ref` that can't be resolved fails the build (no `UNKNOWN` rule fallback); the error message includes the endpoint heading (e.g. `POST /customers`) so the broken reference is easy to locate in multi-endpoint specs.
- **`allOf`** — every branch's `required` list is merged into the parent. Use for "this schema is everything in A plus everything in B".
- **`oneOf` with a `discriminator`** — each branch's required fields become conditional on the discriminator value. The discriminator property itself is pinned to the enum of mapping keys as an unconditional required field:
  ```yaml
  schema:
    oneOf:
      - $ref: "#/components/schemas/InvoicePaid"
      - $ref: "#/components/schemas/InvoiceFailed"
    discriminator:
      propertyName: type
      mapping:
        invoice.paid: "#/components/schemas/InvoicePaid"
        invoice.failed: "#/components/schemas/InvoiceFailed"
  ```
  Emits one rule per branch's required fields with a `type="invoice.paid" and (…)` / `type="invoice.failed" and (…)` guard.
- **`oneOf` / `anyOf` without a discriminator** — union-merged: required fields from every branch are accumulated. The generated FEEL is then stricter than the spec implies. Use `discriminator` (above) or `if`/`then` if you need exclusive branches.
- **Composition implies object** — when a property uses `allOf` / `oneOf` / `anyOf` without an explicit `type: object`, the generator still treats it as an object so the inner required fields are honored. No workaround needed.

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
  or not(req.shippingCarrier instance of string))}
```

**`if`/`then`** *(value-conditional)* — *"if this field equals a specific value, those fields are required."* Supported subset: a single-property predicate using `const` or `enum`, with `required: [<that property>]` inside the `if`. Anything outside the subset is skipped with a build warning naming the schema location (see [Diagnostics](#diagnostics)).

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
  or not(req.cardNumber instance of string))}
```

`enum` predicates produce an `in (…)` check, e.g. `req.tier in ("gold", "platinum") and (…)`. Boolean `const` triggers render compactly as the bare path: `const: true` becomes `req.flag and (…)`, `const: false` becomes `not(req.flag) and (…)`.

**Combining triggers.** Multiple triggers for the same field — whether they come from `dependentRequired`, `if`/`then`, or both — OR-merge: `(req.a!=null or req.b="value") and (…)`. A field also listed in the unconditional `required` keeps the stricter unconditional rule.

**Inheritance into nested objects.** When a nested object is conditionally required, its own required fields inherit the parent's triggers, so the inner rules only fire when the parent's condition holds. When a nested object is **not** required at all (plain-optional), its inner required fields are omitted entirely.

### Restrictions

Known limitations of the generator. Specs may use these constructs, but they won't be honored in the emitted FEEL:

- **`if`/`then` predicates** beyond a single-property `const` or `enum` are skipped. No multi-property `if`, no nested logic, no `pattern` / range / length predicates, no `else` branch.
- **`if`/`then` dependents must be sibling property names.** Dot-paths like `card.number` in `then.required` are not honored. Place the `if`/`then` inside the nested object's schema instead — the extractor recurses, so a nested-level `if`/`then` works correctly for its own properties.
- **`anyOf` and `oneOf` without a discriminator** are union-merged: every branch's required fields are accumulated, so the generated FEEL is stricter than the spec implies. Provide a `discriminator` with explicit `mapping` to get per-branch conditional rules, or use `if`/`then`.
- **`additionalProperties` as a schema** (a sub-schema describing allowed value types) is not honored — only the strict boolean `false` case is.
- **Nested triggers AND-vs-OR**: when a conditionally-required parent contains a conditionally-required child, the child's effective guard is the OR of the parent's and child's triggers, not the AND. Edge case; rare in practice.
- **Not yet supported:** `uniqueItems`, `minProperties` / `maxProperties`, `readOnly` / `writeOnly`, format-driven validations beyond `date` / `date-time` / `time` / `email` / `uuid` / `uri`, schema-form `additionalProperties`, and input sources beyond `request.body` (headers, query parameters).

### Diagnostics

When the generator detects a supported-but-warned construct it emits a build warning rather than silently dropping the rule. Each message is prefixed `[<location>] …`, where the location is a field path (e.g. `customer.address`), a `$ref` (e.g. `#/components/schemas/Foo`), or `(root)`. Warned constructs:

- `if`/`then` predicates outside the single-property `const` / `enum` subset — the conditional is skipped.
- `oneOf` without a `discriminator.mapping` — falls back to union-merge.
- Schema-form `additionalProperties` — only `additionalProperties: false` is honored.

The Maven Mojo wires warnings to `getLog().warn(...)` automatically. Programmatic callers consume them via `Builder.withWarningConsumer(Consumer<String>)`; the default is a silent no-op.

## Output modes

### Activation condition (`addResponse=false`)

Boolean expression intended for the connector's `activationCondition` field. Invalid payloads never start a process instance; the caller receives the connector's static fallback (`statusCode: 422`, `body: { message: "activation condition not met" }`).

```feel
# POST /customers
{
  req: request.body,
  rules: [
    {invalid: req.annualIncome=null or not(req.annualIncome instance of number)},
    {invalid: req.customerId=null or not(req.customerId instance of string)},
    {invalid: req.firstName=null or not(req.firstName instance of string) or string length(req.firstName)<1},
    {invalid: req.newsletterConsent=null or not(req.newsletterConsent instance of boolean)},
    {invalid: req.profile=null or not(req.profile instance of context)},
    {invalid: req.profile.bio=null or not(req.profile.bio instance of string) or string length(req.profile.bio)<1}
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
    { id: "customerId-invalid", field: "customerId", invalid: req.customerId=null or not(req.customerId instance of string) },
    { id: "firstName-invalid", field: "firstName", invalid: req.firstName=null or not(req.firstName instance of string) or string length(req.firstName)<1 },
    { id: "newsletterConsent-invalid", field: "newsletterConsent", invalid: req.newsletterConsent=null or not(req.newsletterConsent instance of boolean) },
    { id: "profile-invalid", field: "profile", invalid: req.profile=null or not(req.profile instance of context) },
    { id: "profile.bio-invalid", field: "profile.bio", invalid: req.profile.bio=null or not(req.profile.bio instance of string) or string length(req.profile.bio)<1 }
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

## Pinning BPMN to the generated FEEL

For consumer projects that own both the OpenAPI spec and the BPMN process models — typically a Camunda connector-runtime project — a self-contained helper test under [plugin/src/test/java/com/consid/automation/camunda/WebhookActivationConditionTest.java](plugin/src/test/java/com/consid/automation/camunda/WebhookActivationConditionTest.java) verifies that every webhook event's `activationCondition` in BPMN matches the FEEL block the plugin emitted. Copy the file as-is into the consumer project's `src/test/java/…`.

What it does:

- Walks every `*.bpmn` under `src/test/resources/bpmn/` recursively.
- Picks `bpmn:startEvent` carrying `zeebe:modelerTemplate="io.camunda.connectors.webhook.WebhookConnectorStartMessage.v1"` and `bpmn:intermediateCatchEvent` carrying `io.camunda.webhook.WebhookConnectorIntermediate.v1`.
- Reads each event's `inbound.method`, `inbound.context`, and `activationCondition` from `bpmn:extensionElements/zeebe:properties`.
- Compares the activation condition (with Camunda's leading `=` FEEL marker stripped) against the matching block in `src/test/resources/feel/expected-activation.feel`.

Conventions, encoded in the test:

- Lookup key is `<inbound.method> /inbound/<inbound.context>` (case-insensitive), matching the plugin's `# <METHOD> /inbound/<context>` heading. The BPMN side stores only the bare context; the `/inbound/` prefix is added when looking up.
- Required dependencies: JUnit 5, AssertJ, JDK XML parser. No project-specific imports.

Wire the plugin to emit FEEL straight to the path the test reads:

```xml
<outputFile>${project.basedir}/src/test/resources/feel/expected-activation.feel</outputFile>
```

After `mvn generate-resources` (or any phase that runs the plugin), `mvn test` produces one dynamic test per webhook event. A drift between BPMN and the generated FEEL fails with a message pointing at the BPMN file, event id, and endpoint key. If your spec paths don't share segments with `inbound.context`, edit the inner record's `endpointKey()` to match your scheme.

If the BPMN folder or FEEL fixture isn't present yet (e.g., a fresh checkout before the plugin has run), the test factory returns zero tests instead of failing — so it's safe to commit the helper before the fixtures exist.

## Build

```bash
mvn -pl plugin verify          # tests + 80% line-coverage gate
mvn -pl example -am package    # build plugin and run it against the bundled spec
```
