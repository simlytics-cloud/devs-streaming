package devs.msg.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.time.DecimalSimTime;
import devs.iso.time.DoubleSimTime;
import devs.iso.time.ExactTimeJsonModule;
import devs.iso.time.LongSimTime;
import devs.iso.time.QuantizedSimTime;
import devs.iso.time.SimTime;
import devs.proxy.TimeWireProfile;
import devs.time.Rational;
import devs.utils.DevsObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ExactSimTimeSerializationTest {
  private final ObjectMapper mapper = DevsObjectMapper.buildObjectMapper();

  @Test
  public void decimalRootsPreserveExactValuesAndCanonicalFixtures() throws IOException {
    for (String value : List.of("0", "0.1", "-12.5", "1", "1E+1000", "1E-1000",
        "0.123456789012345678901234567890123456789")) {
      DecimalSimTime time = DecimalSimTime.of(new BigDecimal(value));
      assertRoot(time, "{\"timeType\":\"decimal\",\"value\":\"" + value + "\"}");
    }
    assertRoot(DecimalSimTime.of(new BigDecimal("1.000")),
        "{\"timeType\":\"decimal\",\"value\":\"1\"}");
  }

  @Test
  public void rationalRootsPreserveExactNormalizedValues() throws IOException {
    assertRoot(QuantizedSimTime.of(Rational.of(2, 6)),
        "{\"timeType\":\"rational\",\"numerator\":\"1\",\"denominator\":\"3\"}");
    assertRoot(QuantizedSimTime.of(Rational.of(-7, 60)),
        "{\"timeType\":\"rational\",\"numerator\":\"-7\",\"denominator\":\"60\"}");
    assertRoot(QuantizedSimTime.of(Rational.ZERO),
        "{\"timeType\":\"rational\",\"numerator\":\"0\",\"denominator\":\"1\"}");
    BigInteger numerator = new BigInteger("123456789012345678901234567890123456789");
    assertRoot(QuantizedSimTime.of(Rational.of(numerator, BigInteger.TWO)),
        "{\"timeType\":\"rational\",\"numerator\":\"" + numerator
            + "\",\"denominator\":\"2\"}");
  }

  @Test
  public void infinitiesHaveExplicitExactTags() throws IOException {
    assertRoot(DecimalSimTime.infinity(),
        "{\"timeType\":\"decimal\",\"infinity\":\"positive\"}");
    assertRoot(QuantizedSimTime.infinity(),
        "{\"timeType\":\"rational\",\"infinity\":\"positive\"}");
  }

  @Test
  public void nestedBaseConcreteAndContainerValuesRoundTrip() throws IOException {
    Times original = new Times();
    original.base = DecimalSimTime.infinity();
    original.decimal = DecimalSimTime.of(new BigDecimal("0.1"));
    original.rational = QuantizedSimTime.infinity();
    original.values = List.of(original.decimal, QuantizedSimTime.of(Rational.of(1, 3)),
        original.base, original.rational, LongSimTime.create(7), DoubleSimTime.create(0.25));
    original.named = Map.of("decimal", original.decimal, "rational", original.rational);
    Times restored = mapper.readValue(mapper.writeValueAsString(original), Times.class);
    assertEquals(original.base, restored.base);
    assertEquals(original.decimal, restored.decimal);
    assertEquals(original.rational, restored.rational);
    assertEquals(original.values, restored.values);
    assertEquals(original.named, restored.named);
  }

  @Test
  public void concreteTargetsRejectOtherExactAndLegacyTags() throws IOException {
    for (SimTime time : List.of(DecimalSimTime.of(BigDecimal.ONE),
        QuantizedSimTime.of(Rational.ONE), DecimalSimTime.infinity(), QuantizedSimTime.infinity(),
        LongSimTime.create(1), DoubleSimTime.create(1))) {
      String json = mapper.writeValueAsString(time);
      for (Class<? extends SimTime> target : List.of(DecimalSimTime.class, QuantizedSimTime.class,
          LongSimTime.class, DoubleSimTime.class)) {
        if (!target.isInstance(time)) {
          assertThrows(JsonProcessingException.class, () -> mapper.readValue(json, target));
        }
      }
    }
  }

  @Test
  public void serializeWithTypeWritesOnlyOneLanguageNeutralTag() throws IOException {
    ExplicitlyTyped holder = new ExplicitlyTyped();
    for (SimTime value : List.of(DecimalSimTime.of(new BigDecimal("0.1")),
        QuantizedSimTime.of(Rational.of(1, 3)), DecimalSimTime.infinity(),
        QuantizedSimTime.infinity())) {
      holder.value = value;
      String json = mapper.writeValueAsString(holder);
      assertFalse(json.contains("devs.iso.time"));
      int tags = 0;
      try (JsonParser p = mapper.createParser(json)) {
        while (p.nextToken() != null) {
          if (p.currentToken() == JsonToken.FIELD_NAME && p.currentName().equals("timeType")) {
            tags++;
          }
        }
      }
      assertEquals(1, tags);
    }
  }

  @Test
  public void standaloneModuleWorksWithoutGlobalAnnotationChanges() throws IOException {
    ObjectMapper standalone = new ObjectMapper().registerModule(new ExactTimeJsonModule());
    DecimalSimTime time = DecimalSimTime.of(new BigDecimal("0.1"));
    String json = standalone.writerFor(SimTime.class).writeValueAsString(time);
    assertEquals(time, standalone.readValue(json, SimTime.class));
    assertEquals(JsonTypeInfo.Id.CLASS, SimTime.class.getAnnotation(JsonTypeInfo.class).use());
  }

  @Test
  public void profilesAreMapperLocalAndApplyToBothContexts() throws IOException {
    ObjectMapper exact = DevsObjectMapper.buildObjectMapper(TimeWireProfile.EXACT_TIME_V1);
    ObjectMapper legacy = DevsObjectMapper.buildObjectMapper(TimeWireProfile.LEGACY_NUMERIC_V1);
    for (ObjectMapper configured : List.of(mapper, legacy, exact)) {
      TimeWireProfile expected = configured == exact ? TimeWireProfile.EXACT_TIME_V1
          : TimeWireProfile.LEGACY_NUMERIC_V1;
      assertEquals(expected,
          configured.getSerializationConfig().getAttributes().getAttribute(TimeWireProfile.class));
      assertEquals(expected,
          configured.getDeserializationConfig().getAttributes().getAttribute(TimeWireProfile.class));
      DecimalSimTime time = DecimalSimTime.of(new BigDecimal("0.1"));
      assertEquals(time, configured.readValue(configured.writeValueAsString(time), SimTime.class));
    }
    assertThrows(NullPointerException.class, () -> DevsObjectMapper.buildObjectMapper(null));
  }

  @Test
  public void rawSourcesRemainAvailableForStreamingResourcePreflight() throws IOException {
    assertTrue(mapper.getFactory().isEnabled(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION));
    String json = "{\"timeType\":\"decimal\",\"value\":\"0.1\"}";
    try (JsonParser p = mapper.createParser(json)) {
      p.nextToken();
      assertNotNull(p.currentTokenLocation().getSourceRef());
    }
    try (JsonParser p = mapper.createParser(json.toCharArray())) {
      assertEquals(DecimalSimTime.of(new BigDecimal("0.1")),
          mapper.readValue(p, SimTime.class));
    }
    assertEquals(DecimalSimTime.of(new BigDecimal("0.1")),
        mapper.readValue(json.getBytes(StandardCharsets.UTF_8), SimTime.class));
  }

  private void assertRoot(SimTime time, String fixture) throws IOException {
    assertEquals(fixture, mapper.writeValueAsString(time));
    assertEquals(fixture, mapper.writerFor(SimTime.class).writeValueAsString(time));
    assertEquals(fixture, mapper.writerFor(time.getClass()).writeValueAsString(time));
    assertEquals(time, mapper.readValue(fixture, SimTime.class));
    assertEquals(time, mapper.readValue(fixture, time.getClass()));
  }

  public static class Times {
    public SimTime base;
    public DecimalSimTime decimal;
    public QuantizedSimTime rational;
    public List<SimTime> values;
    public Map<String, SimTime> named;
  }

  public static class ExplicitlyTyped {
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "timeType")
    public SimTime value;
  }
}