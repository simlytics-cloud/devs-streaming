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

package devs.msg;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import org.immutables.value.Value;

/**
 * A container for Port Values passed to and from a PDevsModel.
 */
@Value.Immutable
@JsonSerialize(as = Bag.class)
@JsonDeserialize(as = Bag.class)
public abstract class AbstractBag {

  /**
   * Retrieves a list of {@link PortValue} instances associated with their respective port
   * identifiers and types. Each {@code PortValue} object encapsulates the value associated with a
   * specific port.
   *
   * @return A list of {@code PortValue<?>} objects representing the values and their associated
   * metadata for various ports.
   */
  public abstract List<PortValue<?>> getPortValueList();

}
