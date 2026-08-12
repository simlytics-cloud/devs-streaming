/*
 * DEVS Streaming Framework Java Copyright (C) 2026 simlytics.cloud LLC and
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

package devs.observation;

import org.apache.pekko.actor.typed.receptionist.ServiceKey;

/**
 * Shared receptionist keys for observation sinks.
 */
public final class ObservationSinkKeys {

  /**
   * Shared receptionist key used by observation sink actors across persistence backends.
   */
  public static final ServiceKey<DevsObservationMessage> OBSERVATION_SINK_KEY =
      ServiceKey.create(DevsObservationMessage.class, "observationSink");

  /**
   * Prevents instantiation of this utility holder.
   */
  private ObservationSinkKeys() {
  }
}