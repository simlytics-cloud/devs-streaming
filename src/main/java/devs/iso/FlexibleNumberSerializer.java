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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import devs.msg.time.DoubleSimTime;
import devs.msg.time.LongSimTime;
import devs.msg.time.SimTime;
import java.io.IOException;

public class FlexibleNumberSerializer extends JsonSerializer<SimTime> {

  @Override
  public void serialize(SimTime simTime, JsonGenerator jsonGenerator, SerializerProvider serializers) throws IOException {
    if (simTime instanceof LongSimTime) {
      // Write as long number
      jsonGenerator.writeNumber(((LongSimTime) simTime).getT());
    } else if (simTime instanceof DoubleSimTime) {
      // Write as double number
      jsonGenerator.writeNumber(((DoubleSimTime) simTime).getT());
    } else {
      throw new IllegalArgumentException("Unsupported SimTime type: " + simTime.getClass().getName());
    }
  }
}