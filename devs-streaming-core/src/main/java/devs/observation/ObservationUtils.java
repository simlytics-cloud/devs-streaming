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
     * Extracts the producer ID from a port name.
     *
     * @param portName The port name.
     * @param separator The separator between producer ID and base port name.
     * @param defaultValue The default value if the separator is not found.
     * @return The producer ID.
     */
    public static String extractProducerId(String portName, String separator, String defaultValue) {
        if (portName != null && portName.contains(separator)) {
            return portName.substring(0, portName.lastIndexOf(separator));
        }
        return defaultValue;
    }

    /**
     * Extracts the producer ID from a port name using the default separator "_".
     *
     * @param portName The port name.
     * @param defaultValue The default value if the separator is not found.
     * @return The producer ID.
     */
    public static String extractProducerId(String portName, String defaultValue) {
        return extractProducerId(portName, "_", defaultValue);
    }
}
