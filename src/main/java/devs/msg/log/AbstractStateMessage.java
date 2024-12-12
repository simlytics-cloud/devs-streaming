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

package devs.msg.log;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import devs.msg.time.SimTime;
import devs.msg.time.TimedDevsMessage;
import org.immutables.value.Value;

/**
 * Interface used to mark messages to be logged by a StateLoggingSimulator.
 */
@Value.Immutable
@JsonSerialize(as = StateMessage.class)
@JsonDeserialize(as = StateMessage.class)
public abstract class AbstractStateMessage<T extends SimTime, S>
    implements TimedDevsMessage<T>, DevsLogMessage {

  @Value.Parameter
  public abstract String getModelId();

  @Value.Parameter
  public abstract S getModelState();

  @Value.Parameter
  @Override
  public abstract T getTime();

}
