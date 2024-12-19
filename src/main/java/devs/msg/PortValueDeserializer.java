/*
 * DEVS Streaming Framework Copyright (C) 2023 simlytics.cloud LLC and DEVS Streaming Framework
 * contributors
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
 */

package devs.msg;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;

/**
 * A custom deserializer for the {@code PortValue} class. This deserializer is used to handle the
 * JSON deserialization process for {@code PortValue} objects, including dynamically resolving the
 * type of the value stored within the {@code PortValue}.
 *
 * <ul>
 *   <li>Parses the JSON structure to extract the port type, port identifier, and value.</li>
 *   <li>Dynamically determines the value's class type based on the {@code portType} field in the
 *   JSON input.</li>
 *   <li>Converts the JSON value into the appropriate type using Jackson's {@code ObjectMapper}.
 *   </li>
 *   <li>Constructs and returns a {@code PortValue} object populated with the deserialized data
 *   and inferred types.</li>
 * </ul>
 * <p>
 * Throws a {@link RuntimeException} in case of a {@link ClassNotFoundException} when trying to
 * load the class type dynamically for the value.
 */
public class PortValueDeserializer extends StdDeserializer<PortValue> {

  public PortValueDeserializer() {
    super(PortValue.class);
  }

  /**
   * Deserializes a JSON payload to a {@code PortValue} object.
   * <p>
   * This method dynamically determines the type of the value stored within the {@code PortValue}
   * based on the {@code portType} field in the JSON structure. It converts the JSON value to the
   * appropriate Java object and populates the fields of the {@code PortValue} accordingly.
   *
   * @param jp                     the {@code JsonParser} instance used to parse the JSON
   * @param deserializationContext the deserialization context for handling custom deserialization
   * @return a {@code PortValue} object populated with the deserialized JSON data
   * @throws IOException if an I/O error occurs during deserialization
   */
  @Override
  public PortValue<?> deserialize(JsonParser jp, DeserializationContext deserializationContext)
      throws IOException {
    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    ObjectNode root = mapper.readTree(jp);
    try {
      String portType = root.get("portType").asText();
      Class cls = Class.forName(portType);
      Object value = mapper.convertValue(root.get("value"), cls);
      String portIdentifier = root.get("portIdentifier").asText();
      return new PortValue(value, portIdentifier, portType);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

}
