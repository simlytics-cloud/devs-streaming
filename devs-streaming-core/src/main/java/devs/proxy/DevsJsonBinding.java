package devs.proxy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.iso.DevsMessage;
import devs.iso.DevsSimMessage;
import devs.iso.time.ExactTimeJsonCodec;
import devs.iso.time.SimTime;
import devs.utils.DevsObjectMapper;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Shared, explicitly configured DEVS JSON envelope and time policy. */
public final class DevsJsonBinding {
  /** Envelope marker required by the exact-time wire profile. */
  public static final String EXACT_PROFILE = "devs-exact-time-v1";
  private static final Set<String> RESERVED_CONTEXT = Set.of("enhancedClock", "enhancedContext",
      "clockContext", "clockId", "compositionOrigin", "executionClock", "coordinatorExecutionConfig", "parent");
  private final TimeEndpointPolicy policy;
  private final ObjectMapper mapper;

  /**
   * Creates a binding whose mapper and validation use the same endpoint policy.
   *
   * @param policy the non-null wire profile and permitted timestamp types
   */
  public DevsJsonBinding(TimeEndpointPolicy policy) {
    this.policy = Objects.requireNonNull(policy);
    this.mapper = DevsObjectMapper.buildObjectMapper(policy.profile());
  }

  /**
   * Validates transport eligibility without adapting timestamps.
   *
   * @param message a simulation message or local initialization wrapper
   * @return the portable message, unwrapped when necessary
   * @throws IllegalArgumentException if the message or its timestamps violate the policy
   */
  public DevsSimMessage validate(DevsMessage message) {
    return TimeProfileValidator.validate(message, policy);
  }

  /**
   * Validates and serializes a message, adding the envelope marker for exact-time transport.
   *
   * @param message the message to publish
   * @return JSON in the configured wire profile
   * @throws IllegalArgumentException if transport validation fails
   * @throws JsonProcessingException if JSON serialization fails
   */
  public String encode(DevsMessage message) throws JsonProcessingException {
    DevsSimMessage wire = validate(message);
    String json = mapper.writeValueAsString(wire);
    return policy.profile() == TimeWireProfile.EXACT_TIME_V1
        ? "{\"wireProfile\":\"" + EXACT_PROFILE + "\"," + json.substring(1) : json;
  }

  /**
   * Checks the envelope before deserialization, then validates the decoded timestamps.
   *
   * @param payload a single JSON message envelope
   * @return the validated simulation message
   * @throws IOException if JSON, envelope checks, or decoded timestamp validation fail
   */
  public DevsSimMessage decode(String payload) throws IOException {
    preflight(payload);
    DevsSimMessage message = mapper.readValue(payload, DevsSimMessage.class);
    try {
      return validate(message);
    } catch (IllegalArgumentException exception) {
      throw JsonMappingException.from((JsonParser) null, exception.getMessage());
    }
  }

  /** Rejects incompatible envelope markers, reserved context, and malformed time fields. */
  private void preflight(String payload) throws IOException {
    boolean exact = policy.profile() == TimeWireProfile.EXACT_TIME_V1;
    try (JsonParser parser = mapper.getFactory().createParser(payload)) {
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        throw mismatch(parser);
      }
      String marker = null;
      String type = null;
      Set<String> fields = new HashSet<>();
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        if (!parser.hasToken(JsonToken.FIELD_NAME)) {
          throw mismatch(parser);
        }
        String field = parser.currentName();
        if (!fields.add(field) && (exact || field.equals("wireProfile"))) {
          throw mismatch(parser);
        }
        parser.nextToken();
        if (field.equals("wireProfile")) {
          if (!parser.hasToken(JsonToken.VALUE_STRING) || !exact) {
            throw mismatch(parser);
          }
          marker = parser.getText();
          if (!EXACT_PROFILE.equals(marker)) {
            throw mismatch(parser);
          }
        } else if (exact && field.equals("messageType")) {
          if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            throw mismatch(parser);
          }
          type = parser.getText();
          if (!TimeProfileValidator.MESSAGE_TYPES.contains(type)) {
            throw mismatch(parser);
          }
        } else if (exact && RESERVED_CONTEXT.contains(field)) {
          throw mismatch(parser);
        } else if (field.equals("eventTime") || field.equals("nextInternalTime")) {
          if (parser.hasToken(JsonToken.START_OBJECT)) {
            if (!exact) {
              throw mismatch(parser);
            }
            try {
              ExactTimeJsonCodec.read(parser, SimTime.class);
            } catch (JsonMappingException exception) {
              exception.prependPath(DevsSimMessage.class, field);
              throw exception;
            }
          } else if (parser.hasToken(JsonToken.VALUE_NULL) || parser.hasToken(JsonToken.START_ARRAY)) {
            throw JsonMappingException.from(parser, "malformed-time: invalid " + field);
          }
        } else {
          parser.skipChildren();
        }
      }
      if (parser.nextToken() != null || exact && (marker == null || type == null)) {
        throw mismatch(parser);
      }
    }
  }

  private static JsonMappingException mismatch(JsonParser parser) {
    return JsonMappingException.from(parser, "profile-mismatch: incompatible DEVS envelope");
  }

  /** Safe for transport logs: never includes raw data, class names, or exception source excerpts. */
  public static String diagnostic(Throwable error) {
    for (Throwable current = error; current != null; current = current.getCause()) {
      String message = current.getMessage();
      if (message != null) {
        for (String category : Set.of("malformed-time", "unsupported-time-type", "time-limit-exceeded", "profile-mismatch")) {
          if (message.contains(category)) {
            return category;
          }
        }
      }
    }
    return "malformed-time";
  }
}