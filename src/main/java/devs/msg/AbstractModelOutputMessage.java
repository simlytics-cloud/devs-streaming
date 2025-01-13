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
 * Represents an abstract, immutable message that encapsulates model output, a time value, and the
 * corresponding sender. This class is a message used in a timed Discrete Event System Simulation
 * (DEVS) to convey information about a model's output, the next simulation time, and other
 * time-related data within the DEVS framework.
 *
 * @param <T> the type of time value, which extends the {@link SimTime} class.
 *            <p>
 *            This abstract class implements the {@link devs.msg.time.TimedDevsMessage} interface to
 *            provide the time value of the message, and the {@link Sender} interface to indicate
 *            the sender of the message. It is designed to facilitate clarity and immutability for
 *            simulation steps in the DEVS framework.
 *            <p>
 *            The concrete implementation of this class is generated as {@code ModelOutputMessage}
 *            using the Immutables framework. It is also configured to be serialized and
 *            deserialized using Jackson.
 */
@Value.Immutable
@JsonSerialize(as = ModelOutputMessage.class)
@JsonDeserialize(as = ModelOutputMessage.class)
public abstract class AbstractModelOutputMessage<T extends SimTime>
    implements Sender {

  /**
   * Retrieves the model output encapsulated in a {@link Bag} object. The {@code Bag} contains a
   * list of {@code PortValue<?>} elements representing the output of a model in the DEVS
   * framework.
   *
   * @return a {@link Bag} representing the model's output as a collection of port-value pairs.
   */
  @Value.Parameter
  public abstract Bag getModelOutput();

  /**
   * Retrieves the next simulation time for the message. This method represents the next scheduled
   * simulation time at which the associated model will execute, as determined within the DEVS
   * framework.
   *
   * @return the next simulation time encapsulated in an object of type {@code T}, which extends the
   * {@code SimTime} class.
   */
  @Value.Parameter
  public abstract T getNextTime();

  /**
   * Retrieves the identifier of the sender associated with this message. The sender is typically
   * represented as a string, providing information about the origin of the message in the DEVS
   * framework.
   *
   * @return a string representing the sender's identifier.
   */
  @Value.Parameter
  @Override
  public abstract String getSender();
}
