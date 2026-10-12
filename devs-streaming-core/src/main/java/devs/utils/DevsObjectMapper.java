/*
 * DEVS Streaming Framework Java Copyright (C) 2024 simlytics.cloud LLC and
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
 *
 */

package devs.utils;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.JsonParserDelegate;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.ContextAttributes;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import devs.iso.time.ExactTimeJsonModule;
import devs.iso.DevsSimMessage;
import devs.iso.SimMessageType;
import devs.iso.time.ExactTimeJsonCodec;
import devs.observation.DevsObservationMessage;
import devs.observation.Observation;
import devs.proxy.TimeWireProfile;
import java.io.IOException;
import java.util.Objects;

/**
 * Utility class for creating and configuring a custom Jackson {@code ObjectMapper} instance.
 * <p>
 * The {@code ObjectMapper} constructed by this class provides serialization and deserialization
 * support for various modules, including: - Jdk8Module for Java 8 types like Optional. -
 * GuavaModule for Google Guava types. - JavaTimeModule for Java 8 date and time types.
 * Additionally, it includes a custom deserializer for the {@code PortValue} class.
 * <p>
 * The custom deserializer dynamically handles the type of the value within the {@code PortValue}
 * object based on the JSON structure.
 */
public class DevsObjectMapper {

  /**
   * Builds and configures a custom Jackson {@code ObjectMapper} instance.
   * <p>
   * The returned {@code ObjectMapper} supports serialization and deserialization for several
   * modules, including: - Jdk8Module for Java 8 types such as {@code Optional}. - GuavaModule for
   * Google Guava types. - JavaTimeModule for Java 8 date and time types. - A custom module with
   * {@code PortValueDeserializer} for handling {@code PortValue} objects.
   *
   * @return a configured {@code ObjectMapper} for handling custom serialization and deserialization
   * scenarios, including support for custom data types.
   */
  public static ObjectMapper buildObjectMapper() {
    return buildObjectMapper(TimeWireProfile.LEGACY_NUMERIC_V1);
  }

  /**
   * Builds a mapper with an explicit policy for message time fields and schedule keys.
   *
   * @param profile the non-null time representation selected for serialization and deserialization
   * @return a mapper with exact-time codecs and, for exact transport, envelope metadata handling
   */
  public static ObjectMapper buildObjectMapper(TimeWireProfile profile) {
    ObjectMapper mapper = new ObservationAwareMapper(JsonFactory.builder()
        .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build())
        .registerModule(new Jdk8Module()).registerModule(new GuavaModule())
        .registerModule(new JavaTimeModule())
        .registerModule(new ExactTimeJsonModule())
        .setDefaultAttributes(ContextAttributes.getEmpty()
            .withSharedAttribute(TimeWireProfile.class, Objects.requireNonNull(profile)))
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    if (profile == TimeWireProfile.EXACT_TIME_V1) {
      mapper.addMixIn(DevsSimMessage.class, ExactEnvelopeMixin.class);
    }
    return mapper;
  }

  /** Leaves message type handling to Jackson's polymorphic envelope metadata in the exact profile. */
  private abstract static class ExactEnvelopeMixin {
    /** Suppresses the ordinary bean property to avoid duplicate message type fields. */
    @JsonIgnore
    public abstract SimMessageType getMessageType();
  }

  /** Adds exact-time fragment checks to supported root observation reads and preserves them on copy. */
  private static final class ObservationAwareMapper extends ObjectMapper {
    private ObservationAwareMapper(JsonFactory factory) {
      super(factory);
    }

    private ObservationAwareMapper(ObservationAwareMapper source) {
      super(source);
    }

    /** @return a mapper copy retaining observation-specific parser wrapping */
    @Override
    public ObjectMapper copy() {
      return new ObservationAwareMapper(this);
    }

    /**
     * Wraps root observation parsers to enforce exact-time fragment limits during binding.
     *
     * @param parser the source parser, closed by the superclass after binding
     * @param type the requested root type
     * @return the bound root value
     * @throws IOException if parsing, fragment validation, or binding fails
     */
    @Override
    protected Object _readMapAndClose(JsonParser parser, JavaType type) throws IOException {
      if (type.hasRawClass(Observation.class) || type.hasRawClass(DevsObservationMessage.class)) {
        parser = new ObservationTimeParser(parser);
      }
      return super._readMapAndClose(parser, type);
    }
  }

  /** Tracks root observation time objects and checks decimal fragment size as tokens advance. */
  private static final class ObservationTimeParser extends JsonParserDelegate {
    private int depth;
    private int timeDepth = -1;
    private long start;
    private boolean exact;

    private ObservationTimeParser(JsonParser parser) {
      super(parser);
    }

    /**
     * Advances the delegate while tracking time-object depth and checking exact fragment limits.
     *
     * @return the next token, or {@code null} at the end of input
     * @throws IOException if parsing or exact-time fragment validation fails
     */
    @Override
    public JsonToken nextToken() throws IOException {
      JsonToken token = delegate.nextToken();
      if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
        depth++;
        if (token == JsonToken.START_OBJECT && depth == 2 && "time".equals(delegate.currentName())) {
          timeDepth = depth;
          var location = delegate.currentTokenLocation();
          start = location.getCharOffset() >= 0 ? location.getCharOffset() : location.getByteOffset();
          exact = false;
        }
      } else if (token == JsonToken.VALUE_STRING && depth == timeDepth
          && "timeType".equals(delegate.currentName())) {
        String tag = delegate.getText();
        exact = "decimal".equals(tag);
      }
      check();
      if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
        if (depth == timeDepth) {
          timeDepth = -1;
          exact = false;
        }
        depth--;
      }
      return token;
    }

    /**
     * Advances through the checked token path rather than bypassing fragment validation.
     *
     * @return the next field name, or {@code null} if the next token is not a field name
     * @throws IOException if parsing or fragment validation fails
     */
    @Override
    public String nextFieldName() throws IOException {
      return nextToken() == JsonToken.FIELD_NAME ? currentName() : null;
    }

    /**
     * Advances through the checked token path and compares the resulting field name.
     *
     * @param name the expected field name
     * @return whether the next token names the requested field
     * @throws IOException if parsing or fragment validation fails
     */
    @Override
    public boolean nextFieldName(com.fasterxml.jackson.core.SerializableString name) throws IOException {
      return nextToken() == JsonToken.FIELD_NAME && name.getValue().equals(currentName());
    }

    /**
     * Consumes nested tokens through this wrapper so skipped fields still receive fragment checks.
     *
     * @return this parser after consuming the current container, if any
     * @throws IOException if parsing or fragment validation fails
     */
    @Override
    public JsonParser skipChildren() throws IOException {
      if (hasToken(JsonToken.START_OBJECT) || hasToken(JsonToken.START_ARRAY)) {
        int nesting = 1;
        while (nesting > 0) {
          JsonToken token = nextToken();
          if (token == null) {
            break;
          }
          if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
            nesting++;
          } else if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
            nesting--;
          }
        }
      }
      return this;
    }

    /**
     * Reads token text and checks the consumed exact-time fragment against codec limits.
     *
     * @return the delegate's current token text
     * @throws IOException if reading or fragment validation fails
     */
    @Override
    public String getText() throws IOException {
      String text = delegate.getText();
      check();
      return text;
    }

    /** Checks active exact-time fragments and adds the observation time path to mapping failures. */
    private void check() throws JsonMappingException {
      if (exact) {
        try {
          ExactTimeJsonCodec.checkObjectFragment(delegate, start);
        } catch (JsonMappingException exception) {
          exception.prependPath(Observation.class, "time");
          throw exception;
        }
      }
    }
  }

  /**
   * Private constructor to prevent instantiation of the utility class.
   * <p>
   * The class is designed to provide static methods for creating and configuring a custom Jackson
   * {@code ObjectMapper}. Instantiation is intentionally restricted as the functionality is
   * accessible directly via static methods.
   */
  private DevsObjectMapper() {
  }
}
