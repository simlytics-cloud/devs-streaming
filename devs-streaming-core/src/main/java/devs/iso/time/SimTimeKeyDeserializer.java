package devs.iso.time;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import devs.proxy.TimeWireProfile;
import java.io.IOException;

/**
 * Reads simulation schedule-map keys written by {@link SimTimeKeySerializer}.
 *
 * <p>Tagged decimal keys require the {@link TimeWireProfile#EXACT_TIME_V1} context
 * attribute and obey {@link TimeWireLimits}. Legacy numeric keys are inferred as long or double
 * from their spelling; {@code LongSimTime:} and {@code DoubleSimTime:} prefixes are also accepted.
 */
public class SimTimeKeyDeserializer extends KeyDeserializer {

  /**
   * Decodes a tagged exact key or a legacy numeric key.
   *
   * @param key the JSON field name, or null
   * @param ctxt the context carrying the selected wire-profile attribute
   * @return the corresponding time, or null if {@code key} is null
   * @throws IOException if an exact key is malformed, exceeds wire limits, or requires a different
   *     wire profile
   * @throws NumberFormatException if a legacy key is not a valid long or double
   */
  @Override
  public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
    if (key == null) {
      return null;
    }
    if (key.startsWith("exact-v1:")) {
      if (ctxt.getAttribute(TimeWireProfile.class) != TimeWireProfile.EXACT_TIME_V1) {
        throw JsonMappingException.from(ctxt, "profile-mismatch: exact schedule keys require exact-v1");
      }
      try {
        ExactTimeJsonCodec.checkStringFragment(ctxt.getParser());
        TimeWireLimits.text(key);
        if (key.startsWith("exact-v1:decimal:")) {
          String text = key.substring("exact-v1:decimal:".length());
          return text.equals("+infinity") ? DecimalSimTime.infinity() : DecimalSimTime.of(TimeWireLimits.decimal(text));
        }

        throw new IllegalArgumentException("unsupported-time-type: unknown exact schedule key discriminator");
      } catch (IllegalArgumentException exception) {
        throw JsonMappingException.from(ctxt, exception.getMessage());
      }
    }

    String trimmed = key.trim();

    // If something wrote keys like "LongSimTime: 1", handle that too.
    if (trimmed.startsWith("LongSimTime")) {
      String[] parts = trimmed.split(":");
      String number = parts.length > 1 ? parts[1].trim() : trimmed;
      return LongSimTime.create(Long.parseLong(number));
    }
    if (trimmed.startsWith("DoubleSimTime")) {
      String[] parts = trimmed.split(":");
      String number = parts.length > 1 ? parts[1].trim() : trimmed;
      return DoubleSimTime.create(Double.parseDouble(number));
    }

    // Default: decide long vs double based on presence of '.' / exponent.
    if (trimmed.contains(".") || trimmed.contains("e") || trimmed.contains("E")) {
      return DoubleSimTime.create(Double.parseDouble(trimmed));
    }
    return LongSimTime.create(Long.parseLong(trimmed));
  }
}
