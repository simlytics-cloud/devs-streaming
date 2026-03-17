package devs.iso.time;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

public class SimTimeKeySerializer extends JsonSerializer<SimTime> {

  @Override
  public void serialize(SimTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
    if (value instanceof LongSimTime longSimTime) {
      gen.writeFieldName(Long.toString(longSimTime.getT()));
      return;
    }
    if (value instanceof DoubleSimTime doubleSimTime) {
      gen.writeFieldName(Double.toString(doubleSimTime.getT()));
      return;
    }
    throw new IllegalArgumentException("Unsupported SimTime type for map key: " + value.getClass().getName());
  }
}
