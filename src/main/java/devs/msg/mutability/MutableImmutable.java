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

package devs.msg.mutability;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Represents a utility interface that provides functionality for processing
 * all fields of a class, including inherited fields. This utility is particularly
 * useful in scenarios where reflective access to all fields (public, private, etc.)
 * of a class, including those declared in its superclasses, is required.
 * <p>
 * The typical usage involves classes implementing this interface to facilitate
 * reflective operations, such as conversion of objects between mutable and
 * immutable forms or any other dynamic field inspection needs.
 * <p>
 * Key features:
 * - Retrieves all declared fields of a class along with those inherited from its
 *   superclasses, up to but not including `java.lang.Object`.
 * - Ensures fields are returned in a consistent order, first including the fields
 *   declared in the class itself, followed by those in its superclasses.
 * - Utilizes reflection APIs to gather field information. Access to private and
 *   protected fields may require appropriate security configurations.
 * <p>
 * Notes:
 * - This method uses the `Field` type from the `java.lang.reflect` package.
 * - The returned array includes fields from both the implementing class and its
 *   superclasses, enabling comprehensive field-based inspections.
 * - Fields from `java.lang.Object` are excluded as they are generally not
 *   specific to application logic.
 * <p>
 * Method:
 * - {@code getAllFields(Class<?> type)}: Retrieves all the declared fields of a class
 *   and its superclasses up until `java.lang.Object`.
 */
public interface MutableImmutable {

  /**
   * Retrieves all fields (including private, protected, and public fields) declared
   * in the specified class and all its superclasses, up to but not including
   * the {@code Object} class.
   * <p>
   * This method uses reflection to collect fields from the provided class as well
   * as its inheritance hierarchy, allowing for comprehensive field inspection.
   *
   * @param type the class whose fields (including inherited ones) are to be retrieved;
   *             must not be null.
   * @return an array of {@code Field} objects representing all declared fields
   *         of the specified class and its superclasses, excluding {@code Object}.
   */
  default Field[] getAllFields(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    while (type != null && !type.equals(Object.class)) {
      fields.addAll(Arrays.asList(type.getDeclaredFields()));
      type = type.getSuperclass();
    }
    return fields.toArray(new Field[0]);

  }

}
