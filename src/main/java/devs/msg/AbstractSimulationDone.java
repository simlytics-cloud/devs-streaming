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
import devs.msg.time.SimTime;
import devs.msg.time.TimedDevsMessage;
import org.immutables.value.Value;

/**
 * Represents an abstract message indicating that a simulation is completed at a specific time. This
 * class is intended to be immutable and is a part of the DEVS (Discrete Event System Specification)
 * messaging framework. It provides a time value representing when the simulation completion
 * occurred.
 *
 * @param <T> the type of time value, extending {@link SimTime}
 *            <p>
 *            Extends the {@link devs.msg.time.TimedDevsMessage}, ensuring that a timestamp is
 *            always associated with the message.
 *            <p>
 *            Annotated with {@link org.immutables.value.Value.Immutable} to generate an immutable
 *            implementation. Supports JSON serialization and deserialization using Jackson
 *            annotations {@link com.fasterxml.jackson.databind.annotation.JsonSerialize} and
 *            {@link com.fasterxml.jackson.databind.annotation.JsonDeserialize}.
 */
@Value.Immutable
@JsonSerialize(as = SimulationDone.class)
@JsonDeserialize(as = SimulationDone.class)
public abstract class AbstractSimulationDone<T extends SimTime> implements TimedDevsMessage<T> {

  /**
   * Retrieves the time value associated with this message. The time represents the simulation time
   * at which the message is relevant or generated.
   *
   * @return the time value of type {@code T}, which extends {@code SimTime}.
   */
  @Value.Parameter
  @Override
  public abstract T getTime();
}
