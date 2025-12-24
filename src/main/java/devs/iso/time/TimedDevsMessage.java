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

package devs.iso.time;

import devs.iso.DevsMessage;

/**
 * A message with a time value.
 *
 * @param <T> the type of time value
 */
public interface TimedDevsMessage<T extends SimTime> extends DevsMessage {

  /**
   * Retrieves the simulation time associated with the message.
   *
   * @return the simulation time of type {@code T}, which extends {@link SimTime}
   */
  T getTime();
}
