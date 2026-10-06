/*
 * DEVS Streaming Framework Java Copyright (C) 2025 simlytics.cloud LLC and
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
package devs.iso;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import devs.iso.time.ExactTimeJsonCodec;
import devs.proxy.TimeWireProfile;
import devs.iso.time.DoubleSimTime;
import devs.iso.time.LongSimTime;
import devs.iso.time.SimTime;
import java.io.IOException;

/**
 * Reads numeric simulation times and, under the exact-time wire profile, tagged exact timestamps.
 * Contextual instances retain a property's declared time type for tagged timestamp validation.
 */
public class FlexibleNumberDeserializer extends JsonDeserializer<SimTime> implements ContextualDeserializer {
  private final Class<? extends SimTime> target;

  /** Creates a deserializer accepting tagged values declared as the general {@link SimTime} type. */
  public FlexibleNumberDeserializer() {
    this(SimTime.class);
  }

  private FlexibleNumberDeserializer(Class<? extends SimTime> target) {
    this.target = target;
  }

  /**
   * Creates a deserializer for a property's declared simulation time type.
   *
   * @param context the Jackson deserialization context
   * @param property the target property, or {@code null} for a root value
   * @return a deserializer carrying the declared time type, defaulting to {@link SimTime}
   */
  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
    Class<?> raw = property == null ? SimTime.class : property.getType().getRawClass();
    return new FlexibleNumberDeserializer(SimTime.class.isAssignableFrom(raw)
        ? raw.asSubclass(SimTime.class) : SimTime.class);
  }

  /**
   * Reads tagged exact values only when enabled, otherwise parses a long with a double fallback.
   *
   * @param jsonParser the parser positioned on the timestamp value
   * @param context the context supplying the wire profile
   * @return the decoded simulation time
   * @throws IOException if a tagged value is malformed or incompatible with the profile or type
   */
  @Override
  public SimTime deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException {
    if (jsonParser.hasToken(JsonToken.START_OBJECT)) {
      if (context.getAttribute(TimeWireProfile.class) != TimeWireProfile.EXACT_TIME_V1) {
        throw JsonMappingException.from(jsonParser, "profile-mismatch: tagged timestamps require exact-v1");
      }
      return ExactTimeJsonCodec.read(jsonParser, target);
    }
    // Extract the number (Jackson will handle decimals vs integers here)
    String value = jsonParser.getText();
    try {
      // First, try to parse as a long
      return LongSimTime.create(Long.parseLong(value));
    } catch (NumberFormatException ex) {
      // If long parsing fails, fall back to double
      return DoubleSimTime.create(Double.parseDouble(value));
    }
  }
}