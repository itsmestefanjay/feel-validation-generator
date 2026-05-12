# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this is

A Maven plugin that reads an OpenAPI 3 document (JSON or YAML) and emits FEEL
validation expressions for Camunda webhook connectors (Inbound/Intermediate).
Output is consumed in the Web Modeler in one of two modes:

- **Activation condition** (`addResponse=false`) — boolean FEEL.
- **Response expression** (`addResponse=true`) — context FEEL with body + status.

See [README.md](README.md) for usage, configuration, and example output.

## Layout

Multi-module Maven reactor (Java 21):

- [pom.xml](pom.xml) — parent POM (`com.consid.automation.camunda:feel-validation-generator-parent`).
- [plugin/](plugin/) — the actual Maven plugin (`packaging=maven-plugin`,
  `artifactId=feel-validation-generator`, goal `generate-feel`).
- [example/](example/) — pom-only consumer that runs the plugin during its
  `generate-resources` phase against
  [example/src/main/resources/openapi/example-api.json](example/src/main/resources/openapi/example-api.json).

## Architecture (plugin module)

Source under [plugin/src/main/java/com/consid/automation/camunda/](plugin/src/main/java/com/consid/automation/camunda/):

- [FEELValidationGeneratorMojo.java](plugin/src/main/java/com/consid/automation/camunda/FEELValidationGeneratorMojo.java) —
  Maven entry point (`@Mojo(name = "generate-feel")`). Validates inputs, then
  delegates.
- [FEELValidationGenerator.java](plugin/src/main/java/com/consid/automation/camunda/FEELValidationGenerator.java) —
  public Builder-based facade. Orchestrates: parse → extract required fields →
  build rules → render → write file.
- [RequiredFieldsExtractor.java](plugin/src/main/java/com/consid/automation/camunda/RequiredFieldsExtractor.java)
  + [FieldTypeResolver.java](plugin/src/main/java/com/consid/automation/camunda/FieldTypeResolver.java) —
  walk the OpenAPI schema (handling `$ref`, `allOf`, `anyOf`, `oneOf`, nested
  objects) to produce a flat `fieldPath → FieldType` map.
- [FEELRuleGenerator.java](plugin/src/main/java/com/consid/automation/camunda/FEELRuleGenerator.java)
  (implements [ValidationRuleBuilder](plugin/src/main/java/com/consid/automation/camunda/ValidationRuleBuilder.java))
  + [FEELExpressionBuilder.java](plugin/src/main/java/com/consid/automation/camunda/FEELExpressionBuilder.java) —
  turn the field map into [ValidationRule](plugin/src/main/java/com/consid/automation/camunda/ValidationRule.java)
  objects and render the final FEEL text.

Keep this separation: OpenAPI traversal stays out of the FEEL renderer, and FEEL
syntax stays out of the traversal.

## Build & test

```bash
mvn -pl plugin -am verify          # build + run unit tests
mvn -pl plugin -am test            # tests only
mvn -pl example -am package        # build plugin then run it on the example
mvn install                        # install both modules locally
```

Generated FEEL from the example lands in
[example/target/generated-feel/customer-validation.feel](example/target/generated-feel/customer-validation.feel).

Integration tests under
[plugin/src/test/java/.../AbstractFEELValidationGeneratorIntegrationTest.java](plugin/src/test/java/com/consid/automation/camunda/AbstractFEELValidationGeneratorIntegrationTest.java)
compare generated output against fixtures in
[plugin/src/test/resources/feel/](plugin/src/test/resources/feel/) and execute
the generated FEEL against the Camunda `feel-engine` with payloads from
[plugin/src/test/resources/payloads/](plugin/src/test/resources/payloads/). When
you change FEEL output, update both the expected `.txt` fixture and any payload
that depends on the new shape — running the engine catches divergence the
string-diff alone misses.

## Conventions

- **Java 21.** Use modern APIs: `java.nio.file.Files/Path`, records where they
  fit, `List.of`, switch expressions. Avoid resurrecting `java.io.File` chains.
- **Pure library — no DI framework.** Do not introduce Spring, Guice, CDI, or
  similar. Wiring is done by hand in constructors / the Builder. New
  collaborators get passed in, not autowired.
- **Minimal dependencies.** Current runtime deps are `swagger-parser`,
  `jackson-databind`, and `slf4j-simple`; Maven APIs are `provided`. Don't add
  a dependency without a clear reason — and prefer the JDK first. Test-only
  additions go in `test` scope.
- **Small, focused classes** with one responsibility, matching the existing
  split (parse vs. extract vs. build vs. render). Add a new collaborator before
  bloating an existing class.
- **Public API surface = the Builder + Mojo parameters.** Treat `FEELValidationGenerator.Builder`
  and the `@Parameter` fields on the Mojo as stable; internal classes can change
  freely. If you add a Mojo parameter, mirror it on the Builder (and vice
  versa) so the two front doors stay aligned.
- **Validation at the boundary.** The Mojo validates inputs (file exists,
  status codes in range); internal classes can trust their arguments. Don't
  re-validate in three places.
- **Tests: JUnit 5 + AssertJ + Mockito.** Fixture-driven integration tests are
  the safety net for FEEL output — prefer adding a fixture pair (OpenAPI in,
  expected FEEL out) over asserting fragments in code.
- **Comments & Javadoc.** Keep the existing class-level Javadoc style on public
  types. Inside methods, only comment the non-obvious *why*.
