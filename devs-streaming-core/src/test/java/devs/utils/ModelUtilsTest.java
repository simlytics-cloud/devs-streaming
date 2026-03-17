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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the ModelUtils utility class.
 * <p>
 * This test class is designed to verify the correctness of methods provided by the ModelUtils
 * class. Specifically, it ensures that input strings are sanitized correctly to conform with
 * predefined format or naming conventions.
 * <p>
 * This class uses the JUnit testing framework.
 */
public class ModelUtilsTest {

  /**
   * Tests the functionality of the ModelUtils.toLegalActorName method to ensure that it correctly
   * removes illegal characters from a given input string.
   * <p>
   * The test verifies the sanitization of the input string, replacing or removing characters not
   * allowed by the naming convention and returning the expected sanitized output.
   * <p>
   * This specific test case checks that: - Spaces are removed from the string. - Characters not in
   * the allowed set (alphanumeric, specific special characters) are omitted. - The resulting string
   * matches the expected output after sanitization.
   */
  @Test
  @DisplayName("Test remove illegal characters")
  void testRemoveIllegalCharacters() {
    assert "Test-ActorOne.test".equals(ModelUtils.toLegalActorName("Test-Actor One.test?`"));
  }
}
