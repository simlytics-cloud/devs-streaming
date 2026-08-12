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

/**
 * Utility class for working with observations.
 */
public class ObservationUtils {

  /**
   * Prevents instantiation of this utility class.
   */
  private ObservationUtils() {
  }

  /**
   * Extracts a producer identifier from a qualified port name.
   *
   * @param portName port name that may embed a producer identifier
   * @param separator separator between the producer identifier and the base port name
   * @param defaultValue fallback value when no embedded producer identifier is present
   * @return the extracted producer identifier or the provided default value
   */
  public static String extractProducerId(String portName, String separator, String defaultValue) {
    if (portName != null && portName.contains(separator)) {
      return portName.substring(0, portName.lastIndexOf(separator));
    }
    return defaultValue;
  }

  /**
   * Extracts a producer identifier from a qualified port name using the default separator.
   *
   * @param portName port name that may embed a producer identifier
   * @param defaultValue fallback value when no embedded producer identifier is present
   * @return the extracted producer identifier or the provided default value
   */
  public static String extractProducerId(String portName, String defaultValue) {
    return extractProducerId(portName, "_", defaultValue);
  }
}
