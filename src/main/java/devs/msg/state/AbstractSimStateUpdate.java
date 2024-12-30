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

package devs.msg.state;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

/**
 * An subsequent message sent to inform external models of the updated state of an object. The state
 * object of type T will have only the changed fields set.
 *
 * @param <T> the type of state object
 */
@Value.Immutable
@JsonSerialize(as = SimStateUpdate.class)
@JsonDeserialize(as = SimStateUpdate.class)
public abstract class AbstractSimStateUpdate<T extends SimState<T>> implements SimStateMessage<T> {

  /**
   * Get the unique identifier of his state object.
   *
   * @return the state identifier
   */
  @Override
  public abstract String getStateId();

  /**
   * Get the state object of type T.
   *
   * @return the state object
   */
  @Override
  public abstract T getStateUpdate();
}
