package devs.iso.time;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import java.io.IOException;

public class SimTimeKeyDeserializer extends KeyDeserializer {

  @Override
  public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
    if (key == null) {
      return null;
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
