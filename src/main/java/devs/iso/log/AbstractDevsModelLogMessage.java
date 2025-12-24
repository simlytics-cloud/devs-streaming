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
import devs.iso.DevsMessage;
import devs.iso.time.SimTime;
import devs.iso.time.TimedDevsMessage;
import org.immutables.value.Value;


/**
 * Represents a log message specific to a DEVS model in a simulation. This abstract class combines
 * the functionality of a DEVS logging message and a time-stamped message, encapsulating the state
 * of a model at a particular simulation time along with an associated DEVS message.
 *
 * @param <T> The type representing simulation time, which must extend {@link SimTime}.
 *            <p>
 *            This class is designed with immutability in mind using the Immutable pattern. It is
 *            serialized and deserialized with Jackson annotations to support seamless JSON
 *            representation.
 *            <p>
 *            Implements: - {@link DevsLogMessage}: A base interface for any log message used within
 *            the DEVS framework. - TimedDevsMessage: Adds time-related
 *            information, associating the message with a specific simulation time.
 *            <p>
 *            Key Features: - Encapsulation of the model's unique identifier, simulation time, and
 *            associated DEVS message. - Built-in support for JSON serialization and
 *            deserialization. - Provides methods to access the time, model identifier, and DEVS
 *            message.
 *            <p>
 *            This class adheres to the Immutable Object principle, ensuring thread-safety, and
 *            consistency.
 */
@Value.Immutable
@JsonSerialize(as = DevsModelLogMessage.class)
@JsonDeserialize(as = DevsModelLogMessage.class)
public abstract class AbstractDevsModelLogMessage<T extends SimTime>
    implements DevsLogMessage, TimedDevsMessage<T> {

  /**
   * Retrieves the simulation time associated with the message. This represents the exact moment in
   * the simulation timeline when the message was generated or is relevant.
   *
   * @return The simulation time of type {@code T}, which extends {@link SimTime}.
   */
  @Override
  @Value.Parameter
  public abstract T getTime();

  /**
   * Retrieves the identifier of the DEVS model associated with this message. This identifier
   * uniquely represents the model in the simulation context.
   *
   * @return A string representing the unique identifier of the model.
   */
  @Value.Parameter
  public abstract String getModelId();

  /**
   * Retrieves the DEVS message associated with this log entry. This message encapsulates the data
   * or state transitions relevant to the DEVS model at the given simulation time.
   *
   * @return The {@code DevsMessage} instance linked with this log message.
   */
  @Value.Parameter
  public abstract DevsMessage getDevsMessage();
}
