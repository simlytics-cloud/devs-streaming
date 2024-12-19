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
 * Abstract base class representing a timed message with a sender in the DEVS Streaming Framework.
 * This class serves as an immutable implementation for messages that include a time value and a
 * sender identifier. It extends the functionality of {@link devs.msg.time.TimedDevsMessage} and
 * {@link devs.msg.Sender}.
 *
 * @param <T> the type of the time value, extending {@link SimTime}
 */
@Value.Immutable
@JsonSerialize(as = ModelDone.class)
@JsonDeserialize(as = ModelDone.class)
public abstract class AbstractModelDone<T extends SimTime> implements TimedDevsMessage<T>, Sender {

  /**
   * Retrieves the time value associated with a message.
   *
   * @return the time value of type {@code T}, where {@code T} extends {@link devs.msg.time.SimTime}
   */
  @Value.Parameter
  @Override
  public abstract T getTime();

  @Value.Parameter
  @Override
  public abstract String getSender();
}
