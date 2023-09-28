/*
 * DEVS Streaming Framework
 * Copyright (C) 2023  simlytics.cloud LLC and DEVS Streaming Framework contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package devs.msg;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;

public class PortValueDerserializer extends StdDeserializer<PortValue> {

  public PortValueDerserializer() {
    super(PortValue.class);
  }

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
