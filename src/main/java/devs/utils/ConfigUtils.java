/*
 * DEVS Streaming Framework Java Copyright (C) 2024 simlytics.cloud LLC and
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

package devs.utils;

import com.typesafe.config.Config;
import java.util.Properties;

/**
 * Utility class for handling configuration-related operations.
 */
public class ConfigUtils {

  /**
   * Converts a Config object to a Properties object by extracting each entry's key and unwrapped
   * value from the configuration and adding them to the Properties instance.
   *
   * @param config the source configuration object containing key-value pairs to be converted
   * @return a Properties object containing the same key-value pairs as the provided configuration
   */
  public static Properties toProperties(Config config) {
    Properties properties = new Properties();
    config.entrySet()
        .forEach(entry -> properties.put(entry.getKey(), entry.getValue().unwrapped()));
    return properties;
  }


  /**
   * Creates and returns a copy of the provided Properties object.
   *
   * @param original the Properties object to be copied
   * @return a new Properties object containing the same key-value pairs as the input
   */
  public static Properties copyProperties(Properties original) {
    Properties copy = new Properties();
    copy.putAll(original);
    return copy;
  }

  /**
   * Private constructor to prevent instantiation of the utility class.
   * <p>
   * This constructor is intentionally defined as private to ensure that the class cannot be
   * instantiated. The class is meant to provide static utility methods only.
   */
  private ConfigUtils() {
  }

}
