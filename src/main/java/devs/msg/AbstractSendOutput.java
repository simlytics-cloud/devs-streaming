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
 * Abstract base class for output messages in a DEVS model that are associated with a specific
 * simulation time.
 *
 * @param <T> the time type, which must extend {@link SimTime}
 */
@Value.Immutable
@JsonSerialize(as = SendOutput.class)
@JsonDeserialize(as = SendOutput.class)
public abstract class AbstractSendOutput<T extends SimTime> implements TimedDevsMessage<T> {

  /**
   * Retrieves the simulation time associated with this DEVS model output message.
   *
   * @return the simulation time of type T, where T extends {@code SimTime}.
   */
  @Value.Parameter
  @Override
  public abstract T getTime();
}
