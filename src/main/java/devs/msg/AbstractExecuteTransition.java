/*
 * DEVS Streaming Framework
 * Copyright (C) 2023  simlytics.cloud LLC and DEVS Streaming Framework contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package devs.msg;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import devs.msg.time.SimTime;
import devs.msg.time.TimedDevsMessage;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * A message telling a PDevsSimulator to execute state transition.  If inputs are included,
 * its associated PDevsModel will execute an external or confluent state transition.
 * If inputs are not included, it will execute an internal state transition.
 *
 * @param <T> the time type
 */
@Value.Immutable
@JsonSerialize(as = ExecuteTransition.class)
@JsonDeserialize(as = ExecuteTransition.class)
public abstract class AbstractExecuteTransition<T extends SimTime> implements TimedDevsMessage<T> {

  @Value.Parameter
  public abstract Optional<Bag> getModelInputsOption();

  @Value.Parameter
  @Override
  public abstract T getTime();

}
