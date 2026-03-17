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

package devs.msg;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import devs.observation.DevsObservationMessage;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Represents a simulation run.
 */
@Value.Immutable
@JsonSerialize(as = Run.class)
@JsonDeserialize(as = Run.class)
public abstract class AbstractRun implements DevsObservationMessage {

  /**
   * Unique identifier for the run (UUID).
   */
  public abstract String getId();

  /**
   * Human-readable name of the simulation run.
   */
  public abstract String getName();

  /**
   * Wall-clock start time.
   */
  public abstract Instant getStartTime();

  /**
   * Current status of the run.
   */
  public abstract RunStatus getStatus();

  /**
   * Configuration parameters for the run.
   */
  public abstract Map<String, Object> getConfig();

  /**
   * Status enumeration for a simulation run.
   */
  public enum RunStatus {
    PENDING, RUNNING, COMPLETED, FAILED
  }
}
