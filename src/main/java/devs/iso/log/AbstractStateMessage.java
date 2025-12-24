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

package devs.iso.log;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import devs.iso.time.SimTime;
import devs.iso.time.TimedDevsMessage;
import org.immutables.value.Value;

/**
 * Interface used to mark messages to be logged by a StateLoggingSimulator.
 */
@Value.Immutable
@JsonSerialize(as = StateMessage.class)
@JsonDeserialize(as = StateMessage.class)
public abstract class AbstractStateMessage<T extends SimTime, S>
    implements TimedDevsMessage<T>, DevsLogMessage {

  /**
   * Retrieves the unique identifier of the DEVS model associated with this message.
   *
   * @return A string representing the unique identifier of the model.
   */
  @Value.Parameter
  public abstract String getModelId();

  /**
   * Retrieves the state of the DEVS model associated with this message.
   *
   * @return The state of type {@code S}, representing the current state of the DEVS model.
   */
  @Value.Parameter
  public abstract S getModelState();

  /**
   * Retrieves the simulation time associated with this message. This value represents the specific
   * point in the simulation timeline when the message was generated or is relevant.
   *
   * @return The simulation time of type {@code T}, which extends {@link SimTime}.
   */
  @Value.Parameter
  @Override
  public abstract T getTime();

}
