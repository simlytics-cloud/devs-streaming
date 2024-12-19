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
 * Represents an immutable message with a time value and a sender within the DEVS framework. This
 * abstract class provides the base implementation for a message that includes both a timestamp and
 * a sender identifier. It serves as a foundational structure for creating specific message types in
 * simulation systems.
 *
 * @param <T> the type of time value extending from {@link SimTime}
 */
@Value.Immutable
@JsonSerialize(as = NextTime.class)
@JsonDeserialize(as = NextTime.class)
public abstract class AbstractNextTime<T extends SimTime> implements TimedDevsMessage<T>, Sender {

  /**
   * Retrieves the time value associated with this message. The time value is represented as an
   * instance of {@code T}, which extends {@link SimTime}.
   *
   * @return the time value of type {@code T} associated with this message.
   */
  @Value.Parameter
  @Override
  public abstract T getTime();

  /**
   * Retrieves the sender identifier associated with this message.
   *
   * @return A string representing the sender's unique identifier.
   */
  @Value.Parameter
  @Override
  public abstract String getSender();
}
