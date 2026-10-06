package devs.msg.time;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import devs.iso.time.*;
import devs.time.Rational;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ExactTimeJsonCodecTest {
  private final JsonFactory factory = JsonFactory.builder()
      .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();

  private SimTime read(String json) throws Exception {
    try (JsonParser parser = factory.createParser(json)) {
      parser.nextToken();
      return ExactTimeJsonCodec.read(parser, SimTime.class);
    }
  }

  private String decimal(String value) {
    return "{\"timeType\":\"decimal\",\"value\":\"" + value + "\"}";
  }

  private String rational(String n, String d) {
    return "{\"timeType\":\"rational\",\"numerator\":\"" + n + "\",\"denominator\":\"" + d + "\"}";
  }

  @Test
  void normalizesWithoutFloatingPointOrRounding() throws Exception {
    for (String text : new String[]{"0.1", "1", "-12.5", "1.2300", "-0.00", "1E+10000", "1E-10000",
        "0.12345678901234567890123456789"}) {
      assertEquals(DecimalSimTime.of(new BigDecimal(text)), read(decimal(text)));
      assertInstanceOf(DecimalSimTime.class, read(decimal(text)));
    }
    assertEquals(QuantizedSimTime.of(Rational.of(1, 3)), read(rational("2", "6")));
    assertEquals(QuantizedSimTime.of(Rational.of(1, 60)), read(rational("1", "60")));
    assertEquals(QuantizedSimTime.of(Rational.ZERO), read(rational("-0", "99")));
    assertSame(DecimalSimTime.infinity(), read("{\"timeType\":\"decimal\",\"infinity\":\"positive\"}"));
    assertSame(QuantizedSimTime.infinity(), read("{\"timeType\":\"rational\",\"infinity\":\"positive\"}"));
  }

  @Test
  void rejectsInvalidGrammarAndShapesWithBoundedCategories() {
    for (String text : new String[]{"+1", "01", ".1", "1.", " 1", "1 ", "NaN", "Infinity", "1,2", "0x1", "1e", "--1"}) {
      rejected(decimal(text), "malformed-time");
    }
    for (String denominator : new String[]{"0", "-3", "+3", "03", " 3", "3.0"}) {
      rejected(rational("1", denominator), "malformed-time");
    }
    for (String json : new String[]{"{}", "{\"timeType\":\"decimal\"}",
        "{\"timeType\":\"decimal\",\"value\":1}",
        "{\"timeType\":\"decimal\",\"value\":null}",
        "{\"timeType\":\"decimal\",\"value\":\"1\",\"value\":\"2\"}",
        "{\"timeType\":\"decimal\",\"timeType\":\"decimal\",\"value\":\"1\"}",
        "{\"timeType\":\"decimal\",\"value\":\"1\",\"extra\":\"x\"}",
        "{\"timeType\":\"decimal\",\"value\":\"1\",\"infinity\":\"positive\"}",
        "{\"timeType\":\"rational\",\"infinity\":\"negative\"}"}) {
      rejected(json, json.equals("{}") ? "unsupported-time-type" : "malformed-time");
    }
    rejected("{\"timeType\":\"java.lang.Double\",\"value\":\"1\"}", "unsupported-time-type");
  }

  private void rejected(String json, String category) {
    Exception error = assertThrows(JsonMappingException.class, () -> read(json));
    assertTrue(error.getMessage().contains(category), error.getMessage());
  }

  @Test
  void checksRawAndNormalizedResourceBoundaries() throws Exception {
    String digits = "9".repeat(4096);
    assertEquals(DecimalSimTime.of(new BigDecimal(digits)), read(decimal(digits)));
    assertEquals(QuantizedSimTime.of(Rational.of(new BigInteger(digits), BigInteger.ONE)), read(rational(digits, "1")));
    rejected(decimal(digits + "9"), "time-limit-exceeded");
    rejected(rational(digits + "9", "1"), "time-limit-exceeded");
    rejected(decimal("1E10001"), "time-limit-exceeded");
    rejected(decimal("1E-10001"), "time-limit-exceeded");
    rejected(decimal("1E999999999999999999999999999"), "time-limit-exceeded");
    rejected(decimal("0.1E-10000"), "time-limit-exceeded");
    rejected(decimal("10E10000"), "time-limit-exceeded");
    rejected(decimal(digits + "E10000"), "time-limit-exceeded");
    String object = decimal("1");
    String atLimit = object.substring(0, 1) + " ".repeat(8256 - object.length()) + object.substring(1);
    assertEquals(DecimalSimTime.of(BigDecimal.ONE), read(atLimit));
    rejected(atLimit.substring(0, 1) + " " + atLimit.substring(1), "time-limit-exceeded");
    String escaped = "{\"timeType\":\"decimal\",\"value\":\"" + "\\u0031".repeat(1400) + "\"}";
    rejected(escaped, "time-limit-exceeded");
    assertThrows(IllegalArgumentException.class, () -> ExactTimeJsonCodec.validate(
        DecimalSimTime.of(new BigDecimal("1E10001"))));
    assertThrows(IllegalArgumentException.class, () -> ExactTimeJsonCodec.validate(
        QuantizedSimTime.of(Rational.of(BigInteger.TEN.pow(4096), BigInteger.ONE))));
  }

  @Test
  void readerFragmentLimitAndTypedMismatchAreRejected() throws Exception {
    try (JsonParser parser = factory.createParser(new StringReader("{" + " ".repeat(8256) + "\"timeType\":\"decimal\",\"value\":\"1\"}"))) {
      parser.nextToken();
      assertThrows(JsonMappingException.class, () -> ExactTimeJsonCodec.read(parser, SimTime.class));
    }
    try (JsonParser parser = factory.createParser(rational("1", "1"))) {
      parser.nextToken();
      assertThrows(JsonMappingException.class, () -> ExactTimeJsonCodec.read(parser, DecimalSimTime.class));
    }
  }
}