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
 * Defines an interface for immutable objects that can be converted back into their
 * corresponding mutable counterparts. Classes implementing this interface are expected
 * to provide mechanisms for deep-copied transformations between mutable and immutable forms.
 *
 * Responsibilities:
 * - Provide a mechanism to convert an immutable object to its corresponding mutable version.
 * - Facilitate deep copy operations through the {@code deepCopy()} method.
 * - Ensure compatibility between mutable and immutable representations, with nested
 *   structural transformations handled when applicable.
 *
 * Features:
 * - The {@code deepCopy()} method ensures that a deep-copied instance of the immutable
 *   object is produced, including nested fields and their respective transformations.
 * - The {@code toMutable()} method relies on reflection to locate the associated mutable class
 *   and to map field values appropriately between the immutable and mutable forms.
 *
 * Conventions:
 * - Naming of classes assumes a pairing between mutable and immutable counterparts:
 *   the mutable class name is a prefix of the immutable class name, with the "Immutable"
 *   suffix appended to designate the immutable variant.
 *
 * Implementing Restrictions:
 * - This interface assumes that all implementing classes follow naming conventions for
 *   correct mapping between immutable and mutable forms.
 *
 * Methods:
 * - {@code deepCopy()}: Produces a deep copy of the current immutable object, using
 *   internal mutable-to-immutable-to-mutable workflow.
 * - {@code toMutable()}: Converts the current immutable instance into its corresponding
 *   mutable representation by utilizing reflection techniques to map fields.
 */
public interface Immutable extends MutableImmutable {

  /**
   * Creates a deep copy of the current immutable instance.
   * This method first converts the immutable instance to its mutable counterpart
   * using the {@code toMutable()} method. Then, it converts the mutable instance back
   * to an immutable instance by invoking the {@code toImmutable()} method on the
   * mutable instance. This ensures the creation of a new immutable instance with
   * equivalent field values while preserving immutability.
   *
   * @param <I> the immutable type extending the current immutable class
   * @return a deep copy of the current immutable instance
   */
  default <I extends Immutable> I deepCopy() {
    Mutable mutable = toMutable();
    return mutable.toImmutable();
  }

  /**
   * Converts the current immutable instance to its mutable counterpart.
   * The method utilizes reflection to create an instance of the associated mutable class
   * and copies all non-static fields from the current immutable instance to the mutable instance.
   * Any nested immutable objects within the fields are also converted to their mutable counterparts.
   *
   * @return the mutable instance corresponding to the current immutable instance
   *         with all field values copied and transformed to mutable objects if applicable
   */
  default <M extends Mutable> M toMutable() {
    
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


