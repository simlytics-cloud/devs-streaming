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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = PortValue.class)
@JsonDeserialize(as = PortValue.class)
public abstract class AbstractPortValue<T> {
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.CLASS, 
      include = JsonTypeInfo.As.PROPERTY,
      property = "@class")
  public abstract T getValue();
  public abstract String getPortName();
  @Override
  public String toString() {
    return "PortValue with Port ID " + getPortName() + " and value " + getValue();
  }
}
