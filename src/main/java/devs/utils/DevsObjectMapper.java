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

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import devs.msg.PortValue;
import devs.msg.PortValueDeserializer;

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
    PortValueDeserializer portValueDeserializer = new PortValueDeserializer();
    SimpleModule portValueModule =
        new SimpleModule("PortValueDeserializer", new Version(1, 0, 0, null));
    //portValueModule.addDeserializer(PortValue.class, portValueDeserializer);
    return new ObjectMapper().registerModule(new Jdk8Module()).registerModule(new GuavaModule())
        .registerModule(new JavaTimeModule()).registerModule(portValueModule)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
