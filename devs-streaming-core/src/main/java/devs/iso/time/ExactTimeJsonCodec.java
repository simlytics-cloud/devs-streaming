package devs.iso.time;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonMappingException;
import devs.time.Rational;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Shared JSON binding for {@link DecimalSimTime} and {@link QuantizedSimTime} using the
 * {@code devs-exact-time-v1} timestamp format.
 *
 * <p>Decimal values and rational numerators and denominators are encoded as strings, avoiding
 * floating-point intermediates. Positive infinity is encoded with {@code infinity: "positive"}.
 * Reads and writes enforce {@link TimeWireLimits}; they neither adapt native time units nor grant
 * endpoint permission. Use {@link ExactTimeJsonModule} for standalone mapper registration.
 */
public final class ExactTimeJsonCodec {
  private static final Set<String> FIELDS = Set.of("timeType", "value", "numerator", "denominator", "infinity");

  private ExactTimeJsonCodec() {}

  /**
   * Reads a strict exact-v1 timestamp, rejecting legacy types, unknown fields, and invalid shapes.
   *
   * @param parser the parser positioned on the timestamp's start-object token
   * @param target {@code SimTime.class} or the expected concrete time class
   * @return the exact time, leaving the parser on the end-object token
   * @throws IOException if parsing fails, the timestamp violates wire limits, or its type does not
   *     match {@code target}
   */
  public static SimTime read(JsonParser parser, Class<? extends SimTime> target) throws IOException {
    return readObject(parser, target, false);
  }

  /**
   * Reads a standalone timestamp, also accepting legacy long and double objects with a {@code t}
   * field and a fully qualified {@code timeType}. Exact timestamps retain strict validation.
   *
   * @param parser the parser positioned on the timestamp's start-object token
   * @param target {@code SimTime.class} or the expected concrete time class
   * @return the decoded time, leaving the parser on the end-object token
   * @throws IOException if parsing fails, an exact timestamp violates wire limits, or its type does
   *     not match {@code target}
   */
  public static SimTime readStandalone(JsonParser parser, Class<? extends SimTime> target) throws IOException {
    return readObject(parser, target, true);
  }

  private static SimTime readObject(JsonParser parser, Class<? extends SimTime> target,
      boolean legacy) throws IOException {
    if (!parser.hasToken(JsonToken.START_OBJECT)) {
      throw error(parser, "malformed-time: timestamp must be an object");
    }
    long start = offset(parser, true);
    if (!legacy) {
      preflightFragment(parser, start);
    }
    Map<String, String> fields = new LinkedHashMap<>();
    boolean unknown = false;
    try {
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        if (!legacy) {
          checkOffset(parser, start);
        }
        if (!parser.hasToken(JsonToken.FIELD_NAME)) {
          throw TimeWireLimits.malformed();
        }
        String name = parser.currentName();
        if (fields.containsKey(name)) {
          throw TimeWireLimits.malformed();
        }
        if (!FIELDS.contains(name) && !(legacy && name.equals("t"))) {
          if (legacy) {
            unknown = true;
            parser.nextToken();
            parser.skipChildren();
            continue;
          }
          throw TimeWireLimits.malformed();
        }
        JsonToken token = parser.nextToken();
        if (!legacy) {
          checkOffset(parser, start);
        }
        if (token != JsonToken.VALUE_STRING && !(legacy && name.equals("t")
            && (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT))) {
          throw TimeWireLimits.malformed();
        }
        String text = parser.getText();
        if (!legacy) {
          checkOffset(parser, start);
        }
        TimeWireLimits.text(text);
        fields.put(name, text);
        if (legacy && name.equals("timeType") && !isLegacy(text)) {
          preflightFragment(parser, start);
        }
      }
      if (!legacy || !isLegacy(fields.get("timeType"))) {
        checkOffset(parser, start);
        if (unknown) {
          throw TimeWireLimits.malformed();
        }
      }
      SimTime result = fromFields(fields, legacy);
      if (target != SimTime.class && !target.isInstance(result)) {
        throw new IllegalArgumentException("unsupported-time-type: timestamp does not match requested type");
      }
      return result;
    } catch (IllegalArgumentException exception) {
      throw error(parser, exception.getMessage());
    }
  }

  private static SimTime fromFields(Map<String, String> fields, boolean legacy) {
    String type = fields.get("timeType");
    if (legacy && isLegacy(type)) {
      String t = fields.get("t");
      if (t == null) {
        throw TimeWireLimits.malformed();
      }
      return type.endsWith("LongSimTime") ? LongSimTime.create(Long.parseLong(t))
          : DoubleSimTime.create(Double.parseDouble(t));
    }
    if (!"decimal".equals(type) && !"rational".equals(type)) {
      throw new IllegalArgumentException("unsupported-time-type: unknown exact timestamp discriminator");
    }
    if (fields.containsKey("infinity")) {
      if (!fields.keySet().equals(Set.of("timeType", "infinity"))
          || !"positive".equals(fields.get("infinity"))) {
        throw TimeWireLimits.malformed();
      }
      return type.equals("decimal") ? DecimalSimTime.infinity() : QuantizedSimTime.infinity();
    }
    if (type.equals("decimal")) {
      if (!fields.keySet().equals(Set.of("timeType", "value"))) {
        throw TimeWireLimits.malformed();
      }
      return DecimalSimTime.of(TimeWireLimits.decimal(fields.get("value")));
    }
    if (!fields.keySet().equals(Set.of("timeType", "numerator", "denominator"))) {
      throw TimeWireLimits.malformed();
    }
    BigInteger numerator = TimeWireLimits.integer(fields.get("numerator"), false);
    BigInteger denominator = TimeWireLimits.integer(fields.get("denominator"), true);
    return QuantizedSimTime.of(Rational.of(numerator, denominator));
  }

  private static boolean isLegacy(String type) {
    return "devs.iso.time.LongSimTime".equals(type) || "devs.iso.time.DoubleSimTime".equals(type);
  }

  /**
   * Checks that an exact time and its compact JSON representation fit the wire resource limits.
   *
   * @param value a decimal or rational time, including positive infinity
   * @throws IllegalArgumentException if the type is unsupported or the representation exceeds
   *     {@link TimeWireLimits}
   */
  public static void validate(SimTime value) {
    Map<String, String> fields = fields(value);
    int length = 2;
    for (Map.Entry<String, String> field : fields.entrySet()) {
      length += field.getKey().length() + field.getValue().length() + 6;
    }
    if (length - 1 > TimeWireLimits.MAX_FRAGMENT) {
      throw TimeWireLimits.exceeded();
    }
  }

  /**
   * Writes a validated exact-v1 timestamp object, using compact output even with a pretty printer
   * so added whitespace cannot exceed the fragment limit.
   *
   * @param value a decimal or rational time, including positive infinity
   * @param generator the destination JSON generator
   * @throws IOException if output fails, the type is unsupported, or wire limits are exceeded
   */
  public static void write(SimTime value, JsonGenerator generator) throws IOException {
    Map<String, String> fields;
    try {
      validate(value);
      fields = fields(value);
    } catch (IllegalArgumentException exception) {
      throw JsonMappingException.from(generator, exception.getMessage());
    }
    // Pretty-printer whitespace must not make a maximum-size valid timestamp unreadable.
    if (generator.getPrettyPrinter() != null) {
      StringBuilder fragment = new StringBuilder("{");
      for (Map.Entry<String, String> field : fields.entrySet()) {
        if (fragment.length() > 1) {
          fragment.append(',');
        }
        fragment.append('"').append(field.getKey()).append("\":\"").append(field.getValue()).append('"');
      }
      generator.writeRawValue(fragment.append('}').toString());
      return;
    }
    generator.writeStartObject();
    for (Map.Entry<String, String> field : fields.entrySet()) {
      generator.writeStringField(field.getKey(), field.getValue());
    }
    generator.writeEndObject();
  }

  private static Map<String, String> fields(SimTime value) {
    Map<String, String> fields = new LinkedHashMap<>();
    if (value instanceof DecimalSimTime decimal) {
      fields.put("timeType", "decimal");
      if (decimal.isInfinite()) {
        fields.put("infinity", "positive");
      } else {
        BigDecimal number = decimal.value();
        TimeWireLimits.validateDecimal(number);
        String text = number.signum() == 0 ? "0" : number.toString();
        TimeWireLimits.decimal(text);
        fields.put("value", text);
      }
    } else if (value instanceof QuantizedSimTime rational) {
      fields.put("timeType", "rational");
      if (rational.isInfinite()) {
        fields.put("infinity", "positive");
      } else {
        TimeWireLimits.validateInteger(rational.value().numerator());
        TimeWireLimits.validateInteger(rational.value().denominator());
        fields.put("numerator", rational.value().numerator().toString());
        fields.put("denominator", rational.value().denominator().toString());
      }
    } else {
      throw new IllegalArgumentException("unsupported-time-type: expected decimal or rational timestamp");
    }
    return fields;
  }

  private static JsonMappingException error(JsonParser parser, String message) {
    return JsonMappingException.from(parser, message);
  }

  private static long offset(JsonParser parser, boolean token) {
    var location = token ? parser.currentTokenLocation() : parser.currentLocation();
    return location.getCharOffset() >= 0 ? location.getCharOffset() : location.getByteOffset();
  }

  private static void checkOffset(JsonParser parser, long start) throws JsonMappingException {
    if (start >= 0 && offset(parser, false) - start > TimeWireLimits.MAX_FRAGMENT) {
      throw error(parser, TimeWireLimits.exceeded().getMessage());
    }
  }

  /**
   * Checks an object's source-fragment size before an enclosing polymorphic deserializer buffers
   * tokens and loses source locations. Raw-source inspection is used when available.
   *
   * @param parser the parser reading the timestamp object
   * @param start the character or byte offset of its start-object token, or a negative unavailable
   *     offset
   * @throws JsonMappingException if the fragment exceeds {@link TimeWireLimits#MAX_FRAGMENT}
   */
  public static void checkObjectFragment(JsonParser parser, long start) throws JsonMappingException {
    preflightFragment(parser, start);
    checkOffset(parser, start);
  }

  private static void preflightFragment(JsonParser parser, long start) throws JsonMappingException {
    Object raw = parser.currentTokenLocation().contentReference().getRawContent();
    if (start < 0 || !(raw instanceof String || raw instanceof char[] || raw instanceof byte[])) {
      return;
    }
    int length = raw instanceof String s ? s.length() : raw instanceof char[] c ? c.length : ((byte[]) raw).length;
    boolean string = false;
    boolean escape = false;
    int depth = 0;
    for (long i = start; i < length; i++) {
      if (i - start >= TimeWireLimits.MAX_FRAGMENT) {
        throw error(parser, TimeWireLimits.exceeded().getMessage());
      }
      char c = raw instanceof String s ? s.charAt((int) i)
          : raw instanceof char[] chars ? chars[(int) i] : (char) ((byte[]) raw)[(int) i];
      if (string) {
        if (escape) {
          escape = false;
        } else if (c == '\\') {
          escape = true;
        } else if (c == '"') {
          string = false;
        }
      } else if (c == '"') {
        string = true;
      } else if (c == '{' || c == '[') {
        depth++;
      } else if ((c == '}' || c == ']') && --depth == 0) {
        return;
      }
    }
  }

  /**
   * Checks the source-fragment size of an exact schedule key, including quotes and escapes, when
   * the parser exposes the raw string, character array, or byte array. Otherwise this is a no-op.
   *
   * @param parser the parser positioned on the schedule key token
   * @throws JsonMappingException if the fragment exceeds {@link TimeWireLimits#MAX_FRAGMENT}
   */
  public static void checkStringFragment(JsonParser parser) throws JsonMappingException {
    long start = offset(parser, true);
    Object raw = parser.currentTokenLocation().contentReference().getRawContent();
    if (start < 0 || !(raw instanceof String || raw instanceof char[] || raw instanceof byte[])) {
      return;
    }
    int length = raw instanceof String s ? s.length() : raw instanceof char[] c ? c.length : ((byte[]) raw).length;
    boolean escape = false;
    for (long i = start + 1; i < length; i++) {
      if (i - start >= TimeWireLimits.MAX_FRAGMENT) {
        throw error(parser, TimeWireLimits.exceeded().getMessage());
      }
      char c = raw instanceof String s ? s.charAt((int) i)
          : raw instanceof char[] chars ? chars[(int) i] : (char) ((byte[]) raw)[(int) i];
      if (escape) {
        escape = false;
      } else if (c == '\\') {
        escape = true;
      } else if (c == '"') {
        return;
      }
    }
  }
}