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
import java.lang.reflect.Modifier;


/**
 * Represents an interface for immutable objects that can be converted into their mutable
 * counterparts. Classes implementing this interface are expected to provide logic that enables the
 * conversion from an immutable instance to its corresponding mutable instance. This typically
 * involves reflection-based field copying, as well as ensuring compatibility regarding nested
 * mutability where necessary.
 * <p>
 * The concept hinges upon a naming convention where the mutable and immutable classes share a
 * similar structure, differing primarily by the use of the Immutable prefix in the immutable
 * class's name.
 * <p>
 * Type Parameter:
 *
 * M - The type of the mutable object that the implementing immutable class can convert into.
 * <p>
 * Key Points:
 * - The {@code toMutable()} method is responsible for creating a mutable instance from the
 *   immutable one.
 * - The method relies on reflection to inspect and copy fields from the immutable class to the
 *   mutable class.
 * - Deep copying is handled for nested fields that also support mutability and immutability
 *   transformations.
 * - Static fields are ignored during the conversion process.
 * - The mutable class must define a parameterless constructor to be instantiated via reflection.
 * <p>
 * Default Implementation:
 * The default implementation of {@code toMutable()} identifies the target mutable class by its
 * naming convention, initializes it using the no-argument constructor, and copies field values
 * using reflection. Nested transformations (e.g., from nested immutable objects to their mutable
 * counterparts) are recursively applied as needed.
 * <p>
 * Exceptions:
 * The {@code toMutable()} method throws a {@code RuntimeException} in cases where the
 * transformation process fails due to factors such as missing constructors, inaccessible fields,
 * or class mismatches.
 * <p>
 * Requirements:
 * Classes implementing this interface must:
 * - Define a corresponding mutable class adhering to the naming convention.
 * - Ensure field compatibility between the mutable and immutable versions, both in terms of name
 *   and type.
 * - Handle nested mutability transformations where applicable.
 * <p>
 * Methods:
 * - {@link #toMutable()} Converts this immutable instance to a mutable version, handling field
 *   copying and nested transformations.
 */
public interface Immutable<M extends Mutable> extends MutableImmutable {

  default <I extends Immutable<M>> I deepCopy() {
    M mutable = toMutable();
    return mutable.toImmutable();
  }

  /**
   * Converts the current immutable instance to its corresponding mutable version.
   * The method identifies the mutable class associated with the immutable class,
   * creates an instance of the mutable class, and copies all compatible field values
   * from the immutable instance to the new mutable instance.
   * This process includes handling nested mutability transformations where applicable.
   *
   * @return the mutable version of the current immutable instance
   * @throws RuntimeException if the conversion process fails due to reflection issues,
   *                          such as inaccessible fields, missing constructors, or
   *                          incompatible class definitions.
   */
  default M toMutable() {
    
    try {
      // 1. Figure out the target mutable class
      String immutableClassName = this.getClass().getName();
      String mutableClassName = immutableClassName.replace("Immutable", "");

      Class<?> mutableClass = Class.forName(mutableClassName);
      Object mutableInstance = mutableClass.getDeclaredConstructor().newInstance();

      // 2. Copy fields using reflection

      for (Field field : getAllFields(this.getClass())) {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }

        field.setAccessible(true);
        Object value = field.get(this);

        Object transformedValue = MutabilityUtil.toMutable(value);

        for (Field mutableField : getAllFields(mutableClass)) {
          if (mutableField.getName().equals(field.getName())) {
            mutableField.setAccessible(true);
            mutableField.set(mutableInstance, transformedValue);
            break;
          }
        }
      }

      return (M) mutableInstance;

    } catch (Exception e) {
      throw new RuntimeException("Failed to convert to mutable: " + this.getClass().getName(), e);
    }
  }
}


