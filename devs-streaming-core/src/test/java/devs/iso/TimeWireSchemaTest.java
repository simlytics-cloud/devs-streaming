/*
 * DEVS Streaming Framework Java Copyright (C) 2025 simlytics.cloud LLC and
 * DEVS Streaming Framework Java contributors.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package devs.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TimeWireSchemaTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final JsonSchemaFactory FACTORY =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
  private static final List<String> EXACT_DEFINITIONS = List.of("ExactTimestamp",
      "DecimalTimestamp", "RationalTimestamp", "DecimalInfinity", "RationalInfinity",
      "DecimalString", "IntegerString", "PositiveIntegerString");
  private static JsonNode baseline;
  private static JsonNode successor;
  private static JsonNode openApi;
  private static JsonSchema baselineMessages;
  private static JsonSchema exactMessages;
  private static JsonSchema exactTimestamp;
  private static JsonSchema logicalTime;
  private static JsonSchema numericTime;
  private static JsonSchema nativeStatus;
  private static JsonSchema semanticsSchema;

  @BeforeAll
  static void loadSchemas() throws IOException {
    baseline = JSON.readTree(docs("iso-21175-2-messages.schema.json").toFile());
    successor = JSON.readTree(docs("devs-messages-v2.schema.json").toFile());
    openApi = new ObjectMapper(new YAMLFactory()).readTree(
        docs("time-semantics/time-semantics-open-api-spec.yaml").toFile());
    baselineMessages = FACTORY.getSchema(baseline);
    exactMessages = FACTORY.getSchema(successor);
    exactTimestamp = definition(successor, "ExactTimestamp");
    logicalTime = definition(successor, "LogicalTime");
    numericTime = definition(baseline, "LogicalTime");
    ObjectNode statusDocument = JSON.createObjectNode();
    statusDocument.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    statusDocument.put("$ref", "#/components/schemas/CurrentSimulationTime");
    statusDocument.set("components", openApi.get("components"));
    nativeStatus = FACTORY.getSchema(statusDocument);
    ObjectNode semanticsDocument = statusDocument.deepCopy();
    semanticsDocument.put("$ref", "#/components/schemas/TimeSemantics");
    semanticsSchema = FACTORY.getSchema(semanticsDocument);
  }

  @Test
  void successorPreservesEveryUnrelatedBaselineConstraint() {
    assertEquals("https://simlytics.cloud/schemas/iso-21175-2-messages.schema.json",
        baseline.get("$id").asText());
    assertEquals("number", baseline.at("/$defs/LogicalTime/type").asText());
    assertEquals("https://simlytics.cloud/schemas/devs-messages-v2.schema.json",
        successor.get("$id").asText());
    assertNotEquals(baseline.get("title"), successor.get("title"));
    assertEquals("2", successor.get("x-schema-version").asText());
    assertEquals("devs-exact-time-v1",
        successor.at("/$defs/CommonMessage/properties/wireProfile/const").asText());
    assertFalse(successor.has("allOf"));

    ObjectNode restored = successor.deepCopy();
    for (String metadata : List.of("$id", "title", "description")) {
      restored.set(metadata, baseline.get(metadata));
    }
    restored.remove("x-schema-version");
    ObjectNode definitions = (ObjectNode) restored.get("$defs");
    definitions.remove(EXACT_DEFINITIONS);
    definitions.set("LogicalTime", baseline.at("/$defs/LogicalTime"));
    ObjectNode common = (ObjectNode) definitions.get("CommonMessage");
    ((ObjectNode) common.get("properties")).remove("wireProfile");
    ArrayNode required = (ArrayNode) common.get("required");
    assertEquals("wireProfile", required.get(required.size() - 1).asText());
    required.remove(required.size() - 1);
    assertEquals(baseline, restored,
        "Only schema identity, LogicalTime, exact definitions, and the required marker may change");
  }

  @Test
  void fixedTimestampFixturesDistinguishExactAndNumericSchemas() throws IOException {
    for (JsonNode value : fixture("valid-timestamps.json")) {
      valid(exactTimestamp, value);
      valid(logicalTime, value);
      invalid(numericTime, value);
    }
    for (JsonNode value : fixture("invalid-timestamps.json")) {
      invalid(exactTimestamp, value);
      invalid(logicalTime, value);
    }
    for (String text : List.of("0", "-1", "0.1", "1e0", "9223372036854775807",
        "1.7976931348623157e308")) {
      JsonNode value = JSON.readTree(text);
      valid(numericTime, value);
      valid(logicalTime, value);
      invalid(exactTimestamp, value);
    }
  }

  @Test
  void schemaEnforcesLexicalLengthBoundariesButLeavesArithmeticLimitsToRuntime() {
    valid(exactTimestamp, decimal("1".repeat(8256)));
    invalid(exactTimestamp, decimal("1".repeat(8257)));
    valid(exactTimestamp, rational("9".repeat(4096), "7".repeat(4096)));
    valid(exactTimestamp, rational("-" + "9".repeat(4096), "1"));
    invalid(exactTimestamp, rational("9".repeat(4097), "1"));
    invalid(exactTimestamp, rational("-" + "9".repeat(4097), "1"));
    invalid(exactTimestamp, rational("1", "7".repeat(4097)));
    valid(exactTimestamp, decimal("1E+10001"));
    valid(exactTimestamp, decimal("1E-10001"));
    valid(exactTimestamp, decimal("99E+10000"));
    valid(exactTimestamp, rational("2", "6"));
  }

  @Test
  void completeMessageFixturesCoverTheEntireVocabularyAndRequireTheMarker() throws IOException {
    Set<String> covered = new HashSet<>();
    for (JsonNode fixture : fixture("valid-messages.json")) {
      valid(exactMessages, fixture);
      covered.add(fixture.get("messageType").asText());
      ObjectNode noMarker = fixture.deepCopy();
      noMarker.remove("wireProfile");
      invalid(exactMessages, noMarker);
      ObjectNode wrongMarker = fixture.deepCopy();
      wrongMarker.put("wireProfile", "devs-legacy-numeric-v1");
      invalid(exactMessages, wrongMarker);

      ObjectNode numeric = numericMessage(fixture);
      valid(baselineMessages, numeric);
      invalid(exactMessages, numeric);
      numeric.put("wireProfile", "devs-exact-time-v1");
      valid(exactMessages, numeric);
      valid(baselineMessages, numeric);
      if (fixture.path("eventTime").isObject() || fixture.path("nextInternalTime").isObject()) {
        invalid(baselineMessages, fixture);
      }
    }
    Set<String> vocabulary = new HashSet<>();
    successor.at("/$defs/MessageType/enum").forEach(type -> vocabulary.add(type.asText()));
    assertEquals(vocabulary, covered);
    for (JsonNode value : fixture("invalid-messages.json")) {
      invalid(exactMessages, value);
    }
  }

  @Test
  void everyCommonTimeFieldAcceptsOnlyNumericOrClosedExactObjects() throws IOException {
    JsonNode validTimes = fixture("valid-timestamps.json");
    JsonNode invalidTimes = fixture("invalid-timestamps.json");
    for (JsonNode fixture : fixture("valid-messages.json")) {
      for (String field : List.of("eventTime", "nextInternalTime")) {
        for (JsonNode value : validTimes) {
          ObjectNode message = fixture.deepCopy();
          message.set(field, value);
          valid(exactMessages, message);
          invalid(baselineMessages, message);
        }
        for (JsonNode value : invalidTimes) {
          ObjectNode message = fixture.deepCopy();
          message.set(field, value);
          invalid(exactMessages, message);
        }
      }
    }
  }

  @Test
  void originalRequiredFieldsAndAdditionalPropertyBehaviorRemainEffective() throws IOException {
    for (JsonNode fixture : fixture("valid-messages.json")) {
      String type = fixture.get("messageType").asText();
      JsonNode required = baseline.at("/$defs/" + type + "/allOf/1/required");
      for (JsonNode field : required) {
        ObjectNode incomplete = fixture.deepCopy();
        incomplete.remove(field.asText());
        invalid(exactMessages, incomplete);
        invalid(baselineMessages, numericMessage(incomplete));
      }
      for (String field : List.of("messageId", "senderId", "receiverId")) {
        ObjectNode emptyId = fixture.deepCopy();
        emptyId.put(field, "");
        invalid(exactMessages, emptyId);
      }
      ObjectNode extended = fixture.deepCopy();
      extended.putNull("unrelatedExtension");
      valid(exactMessages, extended);
      valid(baselineMessages, numericMessage(extended));
      if (extended.has("payload")) {
        ObjectNode payload = (ObjectNode) extended.get("payload");
        payload.put("extension", "1".repeat(9000));
        valid(exactMessages, extended);
        payload.putNull("extension");
        invalid(exactMessages, extended);
        invalid(baselineMessages, numericMessage(extended));
      }
    }
  }

  @Test
  void optionalMonitoringAndErrorTimestampsAndTimestampFreeTerminationArePreserved()
      throws IOException {
    for (JsonNode fixture : fixture("valid-messages.json")) {
      String type = fixture.get("messageType").asText();
      if (List.of("ModelTerminated", "MonitoringMessage", "ErrorReport").contains(type)) {
        ObjectNode noTimes = fixture.deepCopy();
        noTimes.remove(List.of("eventTime", "nextInternalTime"));
        valid(exactMessages, noTimes);
        noTimes.remove("wireProfile");
        invalid(exactMessages, noTimes);
        valid(baselineMessages, noTimes);
      }
    }
  }

  @Test
  void openApiRetainsNativeMetadataAndDoesNotExtendStartupDtos() throws IOException {
    JsonNode schemas = openApi.at("/components/schemas");
    assertEquals("2.0.0", openApi.at("/info/version").asText());
    assertEquals(JSON.readTree("[\"int64\",\"decimal-string\",\"float64\"]"),
        schemas.at("/TimeValueEncoding/enum"));
    assertEquals(JSON.readTree("[\"max-finite\",\"string-sentinel\"]"),
        schemas.at("/TimeInfinityPolicy/enum"));
    assertEquals(JSON.readTree("{\"type\":\"object\",\"required\":[\"numerator\",\"denominator\"],"
        + "\"properties\":{\"numerator\":{\"type\":\"integer\",\"format\":\"int64\",\"example\":1},"
        + "\"denominator\":{\"type\":\"integer\",\"format\":\"int64\",\"minimum\":1,\"example\":1}}}"),
        schemas.get("RationalTime"));
    assertEquals("string", schemas.at("/CurrentSimulationTime/properties/value/type").asText());
    assertEquals(JSON.readTree("[\"long\",\"double\",\"decimal\"]"),
        schemas.at("/CurrentSimulationTime/properties/timeType/enum"));
    assertEquals(JSON.readTree("[\"value\",\"sourceMessageType\",\"updatedAt\"]"),
        schemas.at("/CurrentSimulationTime/required"));
    assertEquals(Set.of("runId", "initializationParameters", "kafka", "simulation"),
        propertyNames(schemas.get("StartModelRunRequest")));
    assertEquals(Set.of("simulationId", "modelInstanceId", "coordinatorId", "timeMode", "timeSemantics"),
        propertyNames(schemas.get("SimulationContext")));
    assertEquals(Set.of("mode", "timeSemantics", "realTimeFactor"),
        propertyNames(schemas.get("TimeMode")));
    assertEquals(Set.of("timeDomain", "valueEncoding", "unitSeconds", "timeGranularity", "originOffset",
        "infinityPolicy"), propertyNames(schemas.get("TimeSemantics")));
    assertEquals("#/components/schemas/RationalTime", schemas.at("/TimeGranularity/$ref").asText());
    assertEquals(0, schemas.at("/TimeGranularity/properties/numerator/exclusiveMinimum").asInt());
    assertFalse(baseline.toString().contains("timeGranularity"));
    assertFalse(successor.toString().contains("timeGranularity"));
  }

  @Test
  void granularityIsOptionalForEveryDomainAndEncodingButStrictWhenPresent() throws IOException {
    for (JsonNode domain : openApi.at("/components/schemas/TimeDomain/enum")) {
      for (String encoding : List.of("int64", "float64", "decimal-string")) {
        ObjectNode semantics = nativeSemantics(encoding);
        semantics.put("timeDomain", domain.asText());
        valid(semanticsSchema, semantics);
        semantics.put("unrelatedSupplierExtension", true);
        valid(semanticsSchema, semantics);
        for (String spacing : List.of("{\"numerator\":1,\"denominator\":1}",
            "{\"numerator\":1,\"denominator\":1000}",
            "{\"numerator\":9223372036854775807,\"denominator\":9223372036854775807}")) {
          semantics.set("timeGranularity", JSON.readTree(spacing));
          valid(semanticsSchema, semantics);
        }
        for (String spacing : List.of("null", "{\"numerator\":0,\"denominator\":1}",
            "{\"numerator\":-1,\"denominator\":1}", "{\"numerator\":1,\"denominator\":0}",
            "{\"numerator\":1,\"denominator\":-1}", "{\"numerator\":1,\"denominator\":1.5}",
            "{\"numerator\":\"1\",\"denominator\":1}", "{\"numerator\":1}",
            "{\"spacing\":{\"numerator\":1,\"denominator\":1000}}")) {
          semantics.set("timeGranularity", JSON.readTree(spacing));
          invalid(semanticsSchema, semantics);
        }
        semantics.remove("timeGranularity");
        semantics.set("quantum", JSON.readTree("{\"numerator\":1,\"denominator\":1}"));
        invalid(semanticsSchema, semantics);
        semantics.set("timeGranularity", JSON.readTree("{\"numerator\":1,\"denominator\":1000}"));
        invalid(semanticsSchema, semantics);
        semantics.putNull("quantum");
        invalid(semanticsSchema, semantics);
      }
    }
  }

  @Test
  void nativeStatusDecimalIdentificationIsConditionalNotGloballyRequired() throws IOException {
    ObjectNode legacy = (ObjectNode) JSON.readTree("{\"value\":\"125.0\","
        + "\"sourceMessageType\":\"NextInternalTimeReport\",\"updatedAt\":\"2026-07-01T12:34:56Z\"}");
    valid(nativeStatus, legacy);
    for (String type : List.of("long", "double")) {
      ObjectNode typedLegacy = legacy.deepCopy();
      typedLegacy.put("timeType", type);
      valid(nativeStatus, typedLegacy);
      typedLegacy.set("timeSemantics", nativeSemantics(type.equals("long") ? "int64" : "float64"));
      valid(nativeStatus, typedLegacy);
    }
    ObjectNode missingValue = legacy.deepCopy();
    missingValue.remove("value");
    invalid(nativeStatus, missingValue);
    ObjectNode decimal = legacy.deepCopy();
    decimal.put("timeType", "decimal");
    invalid(nativeStatus, decimal);
    decimal.put("wireProfile", "devs-legacy-numeric-v1");
    invalid(nativeStatus, decimal);
    decimal.put("wireProfile", "devs-exact-time-v1");
    for (String value : List.of("0.1", "1E+1000", "SUPPLIER_INFINITY")) {
      decimal.put("value", value);
      valid(nativeStatus, decimal);
    }
    ObjectNode declaredDecimal = legacy.deepCopy();
    declaredDecimal.set("timeSemantics", nativeSemantics("decimal-string"));
    invalid(nativeStatus, declaredDecimal);
    declaredDecimal.put("wireProfile", "devs-exact-time-v1");
    invalid(nativeStatus, declaredDecimal);
    declaredDecimal.put("timeType", "double");
    invalid(nativeStatus, declaredDecimal);
    declaredDecimal.put("timeType", "decimal");
    valid(nativeStatus, declaredDecimal);
    decimal.put("timeType", "rational");
    invalid(nativeStatus, decimal);
    decimal.put("timeType", "decimal");
    decimal.set("value", decimal("0.1"));
    invalid(nativeStatus, decimal);
  }

  private static Path docs(String file) {
    Path directory = Path.of("").toAbsolutePath();
    while (directory != null) {
      Path candidate = directory.resolve("docs").resolve(file);
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
      directory = directory.getParent();
    }
    throw new IllegalStateException("Cannot locate docs/" + file);
  }

  private static JsonSchema definition(JsonNode document, String name) {
    ObjectNode root = JSON.createObjectNode();
    root.set("$schema", document.get("$schema"));
    root.set("$defs", document.get("$defs"));
    root.put("$ref", "#/$defs/" + name);
    return FACTORY.getSchema(root);
  }

  private static JsonNode fixture(String name) throws IOException {
    try (InputStream stream = TimeWireSchemaTest.class.getResourceAsStream("/time-wire/" + name)) {
      assertNotNull(stream, name);
      JsonNode fixtures = JSON.readTree(stream);
      assertTrue(fixtures.isArray(), name);
      assertFalse(fixtures.isEmpty(), name);
      return fixtures;
    }
  }

  private static ObjectNode decimal(String value) {
    return JSON.createObjectNode().put("timeType", "decimal").put("value", value);
  }

  private static ObjectNode rational(String numerator, String denominator) {
    return JSON.createObjectNode().put("timeType", "rational")
        .put("numerator", numerator).put("denominator", denominator);
  }

  private static ObjectNode numericMessage(JsonNode message) {
    ObjectNode numeric = message.deepCopy();
    numeric.remove("wireProfile");
    for (String field : List.of("eventTime", "nextInternalTime")) {
      if (numeric.has(field)) {
        numeric.put(field, 1.25);
      }
    }
    return numeric;
  }

  private static ObjectNode nativeSemantics(String encoding) throws IOException {
    ObjectNode semantics = (ObjectNode) JSON.readTree("{\"timeDomain\":\"continuous\","
        + "\"unitSeconds\":{\"numerator\":1,\"denominator\":1},"
        + "\"originOffset\":{\"numerator\":0,\"denominator\":1},\"infinityPolicy\":\"string-sentinel\"}");
    semantics.put("valueEncoding", encoding);
    return semantics;
  }

  private static Set<String> propertyNames(JsonNode schema) {
    Set<String> names = new HashSet<>();
    schema.get("properties").fieldNames().forEachRemaining(names::add);
    return names;
  }

  private static void valid(JsonSchema schema, JsonNode value) {
    var errors = schema.validate(value);
    assertTrue(errors.isEmpty(), () -> value + " rejected: " + errors);
  }

  private static void invalid(JsonSchema schema, JsonNode value) {
    assertFalse(schema.validate(value).isEmpty(), () -> "Unexpectedly accepted: " + value);
  }
}