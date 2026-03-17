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
import devs.iso.time.SimTime;
import devs.iso.time.TimedDevsMessage;
import org.immutables.value.Value;

/**
 * Represents a single observation captured from a model.
 *
 * @param <T> The simulation time type.
 * @param <P> The observation payload type.
 */
@Value.Immutable
@JsonSerialize(as = Observation.class)
@JsonDeserialize(as = Observation.class)
public abstract class AbstractObservation<T extends SimTime, P>
    implements TimedDevsMessage<T>, DevsLogMessage {

  /**
   * Reference to the parent run.
   */
  public abstract String getRunId();

  /**
   * Reference to the branch.
   */
  public abstract String getBranchId();

  /**
   * Simulation time when the observation occurred.
   */
  @Override
  public abstract T getTime();

  /**
   * Identifier of the model that produced the observation.
   */
  public abstract String getProducerModel();

  /**
   * Identifier for the observation type (e.g., class name).
   */
  public abstract String getObservationType();

  /**
   * The actual observation data.
   */
  public abstract P getPayload();
}
