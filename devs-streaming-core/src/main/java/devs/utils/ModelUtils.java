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

/**
 * Utility class for model-related operations.
 * <p>
 * This class provides static utility methods to manipulate and work with model-specific data or
 * operations that are typically reused across various components. It includes methods for
 * sanitizing or formatting input names to conform with predefined restrictions or format
 * conventions.
 * <p>
 * This class is not meant to be instantiated.
 */
public class ModelUtils {

  /**
   * Converts the provided name into a legal actor name by removing all disallowed characters. The
   * resulting name only includes alphanumeric characters, along with specific allowed special
   * characters.
   *
   * @param name the original name to be sanitized
   * @return a sanitized string containing only allowed characters
   */
  public static String toLegalActorName(String name) {
    return name.replaceAll("[^a-zA-Z0-9-_.*$+:@&=,!~';]", "");
  }

  /**
   * Private constructor to prevent instantiation of the utility class.
   * <p>
   * The ModelUtils class is intended to be a collection of static utility methods, and thus it is
   * not meant to be instantiated. This constructor ensures that an instance of this class cannot be
   * created.
   */
  private ModelUtils() {
  }
}
