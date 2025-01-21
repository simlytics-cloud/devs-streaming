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
 * Represents an abstract model for a transition done message in a DEVS simulation framework. This
 * class is intended to be immutable and supports JSON serialization and deserialization.
 *
 * @param <T> The type of time value that extends from SimTime.
 *            <p>
 *            This class implements the following interfaces: - TimedDevsMessage: Indicates the
 *            message is associated with a specific time value. - Sender: Provides information about
 *            the sender of the message.
 *            <p>
 *            Attributes: - nextTime: Represents the next simulation time associated with this
 *            transition. - time: The current simulation time for the message. - sender: Identifies
 *            the sender of the message.
 */
@Value.Immutable
@JsonSerialize(as = TransitionDone.class)
@JsonDeserialize(as = TransitionDone.class)
public abstract class AbstractTransitionDone<T extends SimTime>
    implements TimedDevsMessage<T>, Sender {

  /**
   * Retrieves the next simulation time associated with the transition event represented by this
   * message.
   *
   * @return The next simulation time of type {@code T}, representing the time value for the
   * subsequent simulation event.
   */
  @Value.Parameter
  public abstract T getNextTime();

  /**
   * Retrieves the current simulation time associated with this message.
   *
   * @return The current simulation time of type {@code T}, representing the time value associated
   * with this message.
   */
  @Value.Parameter
  @Override
  public abstract T getTime();

  /**
   * Retrieves the identifier of the sender associated with this message.
   *
   * @return A string representing the sender's identifier.
   */
  @Value.Parameter
  @Override
  public abstract String getSender();
}
