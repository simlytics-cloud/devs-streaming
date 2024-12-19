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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import devs.msg.time.SimTime;
import devs.msg.time.TimedDevsMessage;
import org.immutables.value.Value;

/**
 * Represents an abstract base class for initializing a DEVS simulation message which includes a
 * time value.
 * <p>
 * This class serves as a part of the immutable value types, to be used with the DEVS simulation
 * framework.
 *
 * @param <T> the generic type that extends {@link SimTime}, representing the simulation time value
 *            <p>
 *            This implementation is annotated for serialization and deserialization using Jackson
 *            and immutables library.
 *            <p>
 *            Implements {@link devs.msg.time.TimedDevsMessage} to ensure the inclusion of a time
 *            attribute for simulation messages. The immutable types generated from this class will
 *            inherit the defined behaviors and constraints.
 */
@Value.Immutable
@JsonSerialize(as = InitSim.class)
@JsonDeserialize(as = InitSim.class)
public abstract class AbstractInitSim<T extends SimTime> implements TimedDevsMessage<T> {

  /**
   * Retrieves the simulation time encapsulated as a {@code T} instance.
   *
   * @return The time value of type {@code T}, representing the simulation time associated with this
   * message.
   */
  @Value.Parameter
  @Override
  public abstract T getTime();
}
