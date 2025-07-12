/*
 * DEVS Streaming Framework Store Java Copyright (C) 2025 simlytics.cloud LLC and
 * DEVS Streaming Framework Store Java contributors.  All rights reserved.
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
 */

package devs.msg.mutability;

import java.lang.reflect.Field;
import java.util.Arrays;


/**
 * Represents a marker interface for mutable objects that can be converted into an immutable
 * representation. Classes implementing this interface are expected to adhere to a naming
 * convention where their corresponding immutable counterparts have a prefix "Immutable" added
 * to their class name.
 * <p>
 * Classes implementing this interface are required to provide logic that ensures mutability
 * is converted into an immutable form via the {@code toImmutable()} method. This involves
 * utilizing reflection to inspect fields and invoking the builder methods of the corresponding
 * immutable class.
 *
 * The generic type parameter {@code I} extends {@code Immutable<?>} and represents the target
 * immutable type that the implementing class can convert into.
 * <p>
 * Key points:
 * - The {@code toImmutable()} method creates an immutable instance by identifying the appropriate
 *   immutable class corresponding to the mutable class and using its builder pattern methods.
 * - Field values in the mutable instance are transformed into their immutable representations
 *   before assigning them to the immutable object.
 * - Reflection is used to access fields of the class, including private ones.
 * - Convention dictates that the corresponding immutable class should have a "Builder" inner
 *   class, a static "builder" method, and a "build" method for constructing instances.
 * <p>
 * Exceptions:
 * - If the conversion process fails due to any reason (e.g., class not found, field access
 *   issues, missing builder methods), a {@code RuntimeException} is thrown.
 * <p>
 * Note that the implementing classes must ensure their mutability is appropriately implemented
 * to enable seamless conversion to their immutable counterparts.
 */
public interface Mutable extends MutableImmutable {


  /**
   * Converts the current mutable object into its immutable counterpart.
   * The method relies on naming conventions to locate the corresponding
   * immutable class and its builder, then initializes the immutable object
   * using the field values from the current object.
   *
   * @return The immutable version of the current mutable object.
   * @throws RuntimeException if the conversion to immutable fails due
   *         to an error such as class not found, method invocation issues,
   *         or illegal access.
   */
  @SuppressWarnings("unchecked")
  default <I> I toImmutable() {
    
    try {
      // 1. Resolve Immutable class name by convention
      String mutableClassName = this.getClass().getName();
      String immutableClassName =
          mutableClassName.substring(0, mutableClassName.lastIndexOf('.') + 1)
              + "Immutable" + mutableClassName.substring(mutableClassName.lastIndexOf('.') + 1);

      Class<?> immutableClass = Class.forName(immutableClassName);

      // 2. Collect constructor arguments by field order
      Field[] fields = getAllFields(this.getClass());

      Class<?> builderClass = Arrays.stream(immutableClass.getDeclaredClasses())
          .filter(c -> c.getSimpleName().endsWith("Builder"))
          .findFirst().get();
      Object builder = immutableClass.getMethod("builder").invoke(null);

      for (Field field : fields) {
        field.setAccessible(true);
        Object fieldValue = MutabilityUtil.toImmutable(field.get(this));
        String setterMethodName = field.getName();

        builderClass.getMethod(setterMethodName, field.getType())
            .invoke(builder, fieldValue);
      }

      return (I) builderClass.getMethod("build").invoke(builder);

    } catch (Exception e) {
      throw new RuntimeException("Failed to convert to immutable: " + this.getClass().getName(), e);
    }
  }
}
