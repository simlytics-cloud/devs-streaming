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

package devs.iso;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import devs.iso.time.SimTime;
import org.immutables.value.Value;
import org.jspecify.annotations.NullMarked;

@NullMarked
@Value.Immutable
@JsonSerialize(as = ExecuteTransition.class)
@JsonDeserialize(as = ExecuteTransition.class)
public abstract class AbstractExecuteTransition<T extends SimTime> extends DevsSimMessage implements
    TimedMessage<T>, HasPayload<ExecuteTransitionPayload> {

  @Override
  public SimMessageType getMessageType() {
    return SimMessageType.ExecuteTransition;
  }
}
