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

import devs.Port;
import devs.msg.PortValue;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.immutables.value.Value;


/**
 * The {@code ImmutablePort} class represents a specialized port within a DEVS model
 * where the data type is strictly required to be immutable. This ensures that the
 * state of data passed through the port cannot be changed, promoting thread safety
 * and consistency in concurrent environments.
 *
 * @param <I> the immutable data type associated with the port
 */
public class ImmutablePort<I> extends Port<I> {

  /**
   * Constructs an immutable port with a specified identifier and associated data type.
   * This constructor ensures that the provided class type is either an interface or
   * an immutable class. If the class type is not immutable, an IllegalArgumentException
   * is thrown during object creation.
   *
   * @param portIdentifier the unique identifier of the port
   * @param clazz the class type representing the data type associated with the port;
   *              must either be an interface or a class that is immutable
   * @throws IllegalArgumentException if the provided class type is not immutable
   */
  public ImmutablePort(String portIdentifier, Class<I> clazz) {
    super(portIdentifier, clazz);
    if (!clazz.isInterface() && !isImmutable(clazz)) {
      throw new IllegalArgumentException("The class " + clazz.getCanonicalName()
       + " must be immutable");
    }
  }

  


  /**
   * Creates a new {@code PortValue} instance associated with an immutable value.
   * The method validates the immutability of the provided value's class to ensure
   * proper usage with the immutable port.
   *
   * @param value the immutable value to be associated with the port
   * @return a new {@code PortValue<I>} instance containing the specified immutable value
   * @throws IllegalArgumentException if the provided value's class is not immutable
   */
  @Override
  public PortValue<I> createPortValue(I value) {
    if (!isImmutable(value.getClass())) {
      throw new IllegalArgumentException("The class " + value.getClass().getCanonicalName()
       + " must be immutable");
    }
    return super.createPortValue(value);
  }




  /**
   * Determines whether the specified class is immutable.
   *
   * This method evaluates the immutability of a class based on several criteria:
   * - Whether the class implements the {@code Immutable} interface.
   * - Whether the class is a known immutable type, such as an enum, a primitive wrapper, etc.
   * - Whether the class is final to prevent subclassing.
   * - Whether all fields of the class are private, final, and hold immutable values.
   *
   * For reference type fields, their types are also recursively checked to ensure immutability.
   *
   * @param clazz the class under evaluation
   * @return true if the class is considered immutable; false otherwise
   */
  public static boolean isImmutable(Class<?> clazz) {
    // Check if the class implements the Immutable interface
    if (Immutable.class.isAssignableFrom(clazz)) {
      return true;
    }

    // Check if the class is a known immutable type (including Enum, primitive wrappers, etc.)
    if (isKnownImmutable(clazz) || isImmutablesGeneratedClass(clazz)) {
      return true;
    }

    // Check if the class is final
    if (!Modifier.isFinal(clazz.getModifiers())) {
      return false;
    }

    // Check each field
    for (Field field : clazz.getDeclaredFields()) {
      // Field must be private and final
      int modifiers = field.getModifiers();
      if (!Modifier.isPrivate(modifiers) || !Modifier.isFinal(modifiers)) {
        return false;
      }

      // For reference fields, ensure their types are also immutable
      Class<?> fieldType = field.getType();
      if (!fieldType.isPrimitive() && !isKnownImmutable(fieldType)) {
        return false;
      }
    }

    // If all checks pass, the class is considered immutable
    return true;
  }


  /**
   * Determines whether a given class is considered a known immutable type.
   *
   * This method evaluates if the specified class can be categorized as immutable
   * based on predefined criteria such as being a Java primitive, a wrapper class
   * for primitives, an enum, a record, or the `String` class. The evaluation does
   * not involve checking instance-level properties or field mutability.
   *
   * @param clazz the class to be checked for known immutability
   * @return true if the given class is a known immutable type; false otherwise
   */
  private static boolean isKnownImmutable(Class<?> clazz) {
    return clazz.equals(String.class)
        || clazz.equals(Integer.class)
        || clazz.equals(Double.class)
        || clazz.equals(Boolean.class)
        || clazz.equals(Byte.class)
        || clazz.equals(Character.class)
        || clazz.equals(Short.class)
        || clazz.equals(Long.class)
        || clazz.equals(Float.class)
        || clazz.equals(Void.class)
        || clazz.isEnum()
        || clazz.isPrimitive()
        || clazz.isRecord();
  }


  /**
   * Checks if a given class is generated by the Java Immutables framework.
   *
   * @param clazz the class to check
   * @return true if the class is generated by Java Immutables
   */
  public static boolean isImmutablesGeneratedClass(Class<?> clazz) {
    // Check if class name starts with "Immutable"
    if (clazz.getSimpleName().startsWith("Immutable")) {
      return true;
    }

    // Check for the @Value.Immutable annotation on the interface or abstract class
    for (Annotation annotation : clazz.getDeclaredAnnotations()) {
      if (annotation.annotationType().equals(Value.Immutable.class)) {
            return true;
          }
        }

    // Check the superclass or interfaces
    Class<?> superclass = clazz.getSuperclass();
    if (superclass != null && isImmutablesGeneratedClass(superclass)) {
      return true;
    }
    for (Class<?> iface : clazz.getInterfaces()) {
      if (isImmutablesGeneratedClass(iface)) {
        return true;
      }
    }

    return false;
  }

}
