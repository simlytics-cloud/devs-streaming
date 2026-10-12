package devs.iso.time;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import devs.proxy.TimeWireProfile;
import java.io.IOException;

/**
 * Serializes simulation schedule-map keys as JSON field names.
 *
 * <p>Long and double keys use their numeric spelling. Decimal keys use the tagged
 * {@code exact-v1:decimal:} spelling and require the
 * {@link TimeWireProfile#EXACT_TIME_V1} provider attribute. Exact keys are checked against
 * {@link TimeWireLimits} and can be read by {@link SimTimeKeyDeserializer}.
 */
public class SimTimeKeySerializer extends JsonSerializer<SimTime> {

  /**
   * Writes the field name for a supported simulation time.
   *
   * @param value the non-null schedule time
   * @param gen the generator expecting a field name
   * @param serializers the provider carrying the selected wire-profile attribute
   * @throws IOException if writing fails
   * @throws IllegalArgumentException if the type or profile is unsupported or wire limits are
   *     exceeded
   */
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
    if (serializers.getAttribute(TimeWireProfile.class) == TimeWireProfile.EXACT_TIME_V1) {
      ExactTimeJsonCodec.validate(value);
      String key;
      if (value instanceof DecimalSimTime decimal) {
        key = "exact-v1:decimal:" + (decimal.isInfinite() ? "+infinity" : decimal.value().toString());

      } else {
        throw new IllegalArgumentException("unsupported-time-type: invalid exact schedule key");
      }
      TimeWireLimits.text(key);
      gen.writeFieldName(key);
      return;
    }
    throw new IllegalArgumentException("Unsupported SimTime type for map key: " + value.getClass().getName());
  }
}
