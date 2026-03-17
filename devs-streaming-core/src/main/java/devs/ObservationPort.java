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

package devs;

/**
 * A specialized Port used for emitting Observation objects.
 *
 * @param <P> the payload type of the observation
 */
public class ObservationPort<P> extends Port<P> {

  /**
   * Constructs an ObservationPort instance with a specified identifier and payload class.
   *
   * @param portName the unique identifier of the port
   * @param clazz    the class type representing the data type associated with the port
   */
  public ObservationPort(String portName, Class<P> clazz) {
    super(portName, clazz);
  }
}
