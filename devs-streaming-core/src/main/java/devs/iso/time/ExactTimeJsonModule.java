package devs.iso.time;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;

/**
 * Jackson module for standalone {@link SimTime} values, including exact decimal time.
 *
 * <p>Register an instance with an {@code ObjectMapper} to round-trip {@link DecimalSimTime} using exact-v1 string-valued timestamps. Legacy {@link LongSimTime} and
 * {@link DoubleSimTime} retain their numeric {@code t} field and class-name discriminator.
 *
 * <p>The module's type-information override is local to that mapper. Registration does not select
 * a message wire profile, install schedule-map key binding, or authorize remote execution;
 * transport endpoints must declare their own profile and policy.
 */
public final class ExactTimeJsonModule extends SimpleModule {
  /**
   * Creates a module with standalone serializers, deserializers, and a mapper-local time mix-in.
   */
  public ExactTimeJsonModule() {
    super("ExactTimeJsonModule");
    setMixInAnnotation(SimTime.class, StandaloneTimeMixin.class);
    addSerializer(SimTime.class, new StandaloneTimeSerializer());
    addDeserializer(SimTime.class, new StandaloneTimeDeserializer<>(SimTime.class));
    addDeserializer(DecimalSimTime.class, new StandaloneTimeDeserializer<>(DecimalSimTime.class));
    addDeserializer(LongSimTime.class, new StandaloneTimeDeserializer<>(LongSimTime.class));
    addDeserializer(DoubleSimTime.class, new StandaloneTimeDeserializer<>(DoubleSimTime.class));
  }

  /** Suppresses inherited class-name type metadata for this module's standalone binding. */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
  private abstract static class StandaloneTimeMixin {
  }

  /** Writes legacy numeric objects or exact timestamps with their own discriminator. */
  private static final class StandaloneTimeSerializer extends JsonSerializer<SimTime> {
    /**
     * Writes a standalone timestamp while retaining the legacy long and double object shape.
     *
     * @param value the non-null time to encode
     * @param g the output generator
     * @param provider the serialization context
     * @throws IOException if writing fails
     * @throws IllegalArgumentException if an exact value is unsupported or exceeds wire limits
     */
    @Override
    public void serialize(SimTime value, JsonGenerator g, SerializerProvider provider)
        throws IOException {
      if (value instanceof LongSimTime) {
        g.writeStartObject();
        g.writeStringField("timeType", LongSimTime.class.getName());
        g.writeNumberField("t", ((LongSimTime) value).getT());
        g.writeEndObject();
      } else if (value instanceof DoubleSimTime) {
        g.writeStartObject();
        g.writeStringField("timeType", DoubleSimTime.class.getName());
        g.writeNumberField("t", ((DoubleSimTime) value).getT());
        g.writeEndObject();
      } else {
        ExactTimeJsonCodec.write(value, g);
      }
    }

    /**
     * Writes the timestamp's own discriminator instead of adding a second type wrapper.
     *
     * @param value the non-null time to encode
     * @param g the output generator
     * @param provider the serialization context
     * @param typeSerializer the type serializer, intentionally unused
     * @throws IOException if writing fails
     * @throws IllegalArgumentException if an exact value is unsupported or exceeds wire limits
     */
    @Override
    public void serializeWithType(SimTime value, JsonGenerator g, SerializerProvider provider,
        TypeSerializer typeSerializer) throws IOException {
      serialize(value, g, provider);
    }
  }

  /**
   * Reads standalone timestamps and verifies that their discriminator matches the requested type.
   *
   * @param <T> the requested simulation-time type
   */
  private static final class StandaloneTimeDeserializer<T extends SimTime>
      extends JsonDeserializer<T> {
    private final Class<T> target;

    private StandaloneTimeDeserializer(Class<T> target) {
      this.target = target;
    }

    /**
     * Decodes a legacy or exact timestamp with target-type and exact wire-limit checks.
     *
     * @param p the parser positioned on the timestamp object
     * @param context the deserialization context
     * @return the decoded time of the requested type
     * @throws IOException if parsing, type validation, or exact wire-limit validation fails
     */
    @Override
    public T deserialize(JsonParser p, DeserializationContext context) throws IOException {
      return target.cast(ExactTimeJsonCodec.readStandalone(p, target));
    }

    /**
     * Rejects null for concrete exact-time targets while retaining legacy null handling.
     *
     * @param context the deserialization context
     * @return null for targets other than the concrete exact-time classes
     * @throws JsonMappingException if the target is decimal time
     */
    @Override
    public T getNullValue(DeserializationContext context) throws JsonMappingException {
      if (target == DecimalSimTime.class) {
        throw JsonMappingException.from(context, "malformed-time: exact timestamp cannot be null");
      }
      return null;
    }
  }
}