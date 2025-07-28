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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * The MutabilityUtil class provides a set of utility methods for converting objects between mutable
 * and immutable representations, as well as retrieving metadata such as fields and annotations from
 * classes. It aims to facilitate working with collections, maps, and annotated classes in a manner
 * that ensures immutability or mutability based on desired use cases.
 */
public class MutabilityUtil {

  /**
   * Converts an object to its mutable representation if applicable. If the input object implements
   * the {@link Immutable} interface, its {@code toMutable()} method is invoked to obtain a mutable
   * version. If the object already implements the {@link Mutable} interface, it is cast to the
   * desired class type. Otherwise, an {@link IllegalArgumentException} is thrown if the object
   * cannot be converted to a mutable type.
   *
   * @param <I>    the general type of the input object
   * @param <M>    the specific mutable type to which the object should be converted, must extend or
   *               implement {@code I}
   * @param object the object to be converted; must be either {@code Immutable} or {@code Mutable}
   * @param clazz  the class representation of {@code M} against which the object is cast
   * @return a mutable instance of the input object cast to type {@code M}
   * @throws IllegalArgumentException if the object cannot be converted to a mutable type
   */
  public static <I, M extends I> M objectToMutable(I object, Class<M> clazz) {
    if (object instanceof Immutable immutable) {
      return clazz.cast(immutable.toMutable());
    } else if (object instanceof Mutable) {
      return clazz.cast(object);
    } else {
      throw new IllegalArgumentException("Object of type " + object.getClass().getCanonicalName()
          + " cannot be converted to Mubable");
    }
  }


  /**
   * Retrieves all declared fields of a given class, including fields from its superclasses. The
   * search stops at the Object class.
   *
   * @param type the class whose fields are to be retrieved
   * @return an array of Field objects for all declared fields of the class and its superclasses
   */
  public static Field[] getAllFields(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    while (type != null && !type.equals(Object.class)) {
      fields.addAll(Arrays.asList(type.getDeclaredFields()));
      type = type.getSuperclass();
    }
    return fields.toArray(new Field[0]);
  }

  /**
   * Retrieves a map of annotations declared on the given class with their parameter values. Each
   * annotation is represented as a key in the map, with its parameters and their values in a
   * second-level map.
   *
   * @param clazz the class whose annotations and parameters are to be retrieved
   * @return a map where keys are annotation names, and values are maps of parameter names to values
   * @throws RuntimeException if an error occurs while retrieving annotation parameter values
   */
  public static Map<String, Map<String, Object>> getClassAnnotationsAndParameters(Class<?> clazz) {
    return Arrays.stream(clazz.getDeclaredAnnotations())
        .collect(Collectors.toMap(
            annotation -> annotation.annotationType().getName(),
            annotation -> Arrays.stream(annotation.annotationType().getDeclaredMethods())
                .collect(Collectors.toMap(
                    method -> method.getName(),
                    method -> {
                      try {
                        return method.invoke(annotation);
                      } catch (Exception e) {
                        throw new RuntimeException("Could not retrieve annotation parameter values",
                            e);
                      }
                    }
                ))
        ));
  }


  /**
   * Converts the given object to its immutable representation, if applicable. The method checks the
   * input's type and applies appropriate logic to convert it to an immutable form.
   * <p>
   * - Objects implementing {@code Immutable} are returned as-is. - Objects implementing
   * {@code Mutable} are converted using the {@code toImmutable} method. - {@code Collection}
   * objects are converted to immutable collections. - {@code Map} objects are converted to
   * immutable maps. - Other objects are returned as-is.
   *
   * @param <T> the expected type of the immutable object
   * @param obj the object to be converted to an immutable form
   * @return the immutable version of the input object or the input itself if no conversion applies
   */
  @SuppressWarnings("unchecked")
  public static <T> T toImmutable(Object obj) {
    if (obj == null) {
      return null;
    }

    if (obj instanceof Immutable) {
      return (T) obj;
    } else if (obj instanceof Mutable) {
      return (T) ((Mutable) obj).toImmutable();
    } else if (obj instanceof Collection<?>) {
      return (T) toImmutableCollection(obj);// returns List<Object>
    } else if (obj instanceof Map<?, ?>) {
      return (T) toImmutableMap(obj);// returns List<Object>
    } else {
      return (T) obj;
    }
  }


  /**
   * Converts the input object to an immutable collection representation, if applicable. The method
   * checks the type of the input and processes it to produce an immutable counterpart. For non-
   * collections, it delegates the conversion to {@code toImmutable}.
   *
   * @param <T>   the type of the immutable collection
   * @param input the object to convert to an immutable collection
   * @return the immutable version of the input collection or, for non-collections, its immutable
   * form
   */
  @SuppressWarnings("unchecked")
  public static <T> T toImmutableCollection(Object input) {
    if (input instanceof Collection<?> collection) {
      Collection<?> immutableCollection;

      if (collection instanceof ArrayList) {
        immutableCollection = com.google.common.collect.ImmutableList.copyOf(
            collection.stream()
                .map(MutabilityUtil::toImmutable)
                .toList()
        );
      } else if (collection instanceof java.util.LinkedHashSet) {
        immutableCollection =
            collection.stream()
                .map(MutabilityUtil::toImmutable)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
      } else if (collection instanceof java.util.HashSet) {
        immutableCollection = com.google.common.collect.ImmutableSet.copyOf(
            collection.stream()
                .map(MutabilityUtil::toImmutable)
                .toList()
        );
      } else if (collection instanceof java.util.TreeSet) {
        immutableCollection = com.google.common.collect.ImmutableSortedSet.copyOf(
            collection.stream()
                .map(MutabilityUtil::toImmutable)
                .toList()
        );
      } else if (collection instanceof java.util.Stack) {
        immutableCollection =
            collection.stream()
                .map(MutabilityUtil::toImmutable)
                .collect(Collectors.toCollection(java.util.Stack::new));
      } else if (collection instanceof java.util.Vector) {
        immutableCollection =
            collection.stream()
                .map(MutabilityUtil::toImmutable)
                .collect(Collectors.toCollection(java.util.Vector::new));
      } else if (collection instanceof java.util.LinkedList) {
        immutableCollection =
            collection.stream()
                .map(MutabilityUtil::toImmutable)
                .collect(Collectors.toCollection(java.util.LinkedList::new));
      } else if (collection instanceof java.util.PriorityQueue) {
        immutableCollection =
            collection.stream()
                .map(MutabilityUtil::toImmutable)
                .collect(Collectors.toCollection(java.util.PriorityQueue::new));
      } else if (collection instanceof java.util.ArrayDeque) {
        immutableCollection =
            collection.stream()
                .map(MutabilityUtil::toImmutable)
                .collect(Collectors.toCollection(java.util.ArrayDeque::new));
      } else {
        immutableCollection = collection.stream()
            .map(MutabilityUtil::toImmutable)
            .toList();
      }
      return (T) immutableCollection;
    }
    return (T) toImmutable(input);
  }

  /**
   * Converts the input map or object to its immutable representation. If the input is a map, it is
   * converted to an immutable map while maintaining specifics like order in LinkedHashMap or
   * sorting in TreeMap. Non-map inputs are processed by {@code toImmutable}.
   *
   * @param <T>   the type of the immutable representation
   * @param input the object to convert; maps are converted to immutable maps
   * @return the immutable map or immutable version of the input object
   */
  @SuppressWarnings("unchecked")
  public static <T> T toImmutableMap(Object input) {
    if (input instanceof Map<?, ?> map) {
      Map<?, ?> immutableMap;

      if (map instanceof java.util.LinkedHashMap) {
        immutableMap = map.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> toImmutable(e.getValue()),
                (k1, k2) -> k1,
                java.util.LinkedHashMap::new
            ));
      } else if (map instanceof java.util.TreeMap) {
        immutableMap = com.google.common.collect.ImmutableSortedMap.copyOf(
            map.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> toImmutable(e.getValue())
                ))
        );
      } else {
        immutableMap = com.google.common.collect.ImmutableMap.copyOf(
            map.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> toImmutable(e.getValue())
                ))
        );
      }
      return (T) immutableMap;
    }
    return (T) toImmutable(input);
  }


  /**
   * Converts the provided input object to a mutable collection representation, if applicable. This
   * method determines the type of the input collection and creates a mutable counterpart while
   * preserving the original collection's characteristics (e.g., ordering, duplication, etc.). If
   * the input is not a collection, it delegates to the {@code toMutable} method for generic
   * conversion.
   *
   * @param <T>   the expected type of the mutable collection
   * @param input the input object to be converted to a mutable collection; it can be a collection
   *              such as {@code List}, {@code Set}, or {@code Queue}, or any other object
   * @return the mutable version of the input collection, or for non-collection inputs, the
   * converted mutable counterpart
   */
  @SuppressWarnings("unchecked")
  public static <T> T toMutableCollection(Object input) {

    if (input instanceof Collection<?> collection) {
      if (collection instanceof ArrayList) {
        return (T) collection.stream()
            .map(MutabilityUtil::toMutable)
            .collect(Collectors.toCollection(ArrayList::new));
      } else if (collection instanceof com.google.common.collect.ImmutableList) {
        return (T) collection.stream()
            .map(MutabilityUtil::toMutable)
            .collect(Collectors.toCollection(ArrayList::new));
      } else if (collection instanceof java.util.LinkedHashSet) {
        return (T) collection.stream()
            .map(MutabilityUtil::toMutable)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
      } else if (collection instanceof java.util.HashSet) {
        return (T) collection.stream()
            .map(MutabilityUtil::toMutable)
            .collect(Collectors.toCollection(java.util.HashSet::new));
      } else if (collection instanceof com.google.common.collect.ImmutableSortedSet) {
        return (T) collection.stream()
            .map(MutabilityUtil::toMutable)
            .collect(Collectors.toCollection(java.util.TreeSet::new));
      } else if (collection instanceof com.google.common.collect.ImmutableSet) {
        return (T) collection.stream()
            .map(MutabilityUtil::toMutable)
            .collect(Collectors.toCollection(java.util.HashSet::new));
      } else if (collection instanceof java.util.TreeSet) {
        return (T) collection.stream()
            .map(MutabilityUtil::toMutable)
            .collect(Collectors.toCollection(java.util.TreeSet::new));
      } else if (collection instanceof java.util.LinkedList) {
        return (T) collection.stream()
            .map(MutabilityUtil::toMutable)
            .collect(Collectors.toCollection(java.util.LinkedList::new));
      } else if (collection instanceof java.util.Stack) {
        return (T) collection.stream()
            .map(MutabilityUtil::toMutable)
            .collect(Collectors.toCollection(java.util.Stack::new));
      } else if (collection instanceof java.util.Vector) {
        return (T) collection.stream()
            .map(MutabilityUtil::toMutable)
            .collect(Collectors.toCollection(java.util.Vector::new));
      } else if (collection instanceof java.util.PriorityQueue) {
        return (T) collection.stream()
            .map(MutabilityUtil::toMutable)
            .collect(Collectors.toCollection(java.util.PriorityQueue::new));
      } else if (collection instanceof java.util.ArrayDeque) {
        return (T) collection.stream()
            .map(MutabilityUtil::toMutable)
            .collect(Collectors.toCollection(java.util.ArrayDeque::new));
      } else {
        return (T) collection.stream()
            .map(MutabilityUtil::toMutable)
            .collect(Collectors.toCollection(ArrayList::new));
      }
    }
    return (T) toMutable(input);
  }


  /**
   * Converts the provided input object to a mutable map representation, if applicable. This method
   * checks the type of the input and creates a mutable map while preserving the characteristics of
   * the original map (e.g., ordering in {@code LinkedHashMap} or sorting in {@code TreeMap}). For
   * non-map inputs, it delegates to the {@code toMutable} method for generic conversion.
   *
   * @param <T>   the expected type of the mutable map or the mutable version of the input
   * @param input the input object to be converted; if it is a map, a mutable map representation is
   *              returned. For non-map objects, the method delegates to the {@code toMutable}
   *              method for conversion.
   * @return the mutable version of the input map or, for non-map inputs, the mutable counterpart of
   * the object
   */
  @SuppressWarnings("unchecked")
  public static <T> T toMutableMap(Object input) {
    if (input instanceof Map<?, ?> map) {
      if (map instanceof java.util.LinkedHashMap) {
        return (T) map.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> toMutable(e.getValue()),
                (k1, k2) -> k1,
                java.util.LinkedHashMap::new
            ));
      } else if (map instanceof java.util.TreeMap) {
        return (T) map.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> toMutable(e.getValue()),
                (k1, k2) -> k1,
                java.util.TreeMap::new
            ));
      } else {
        return (T) map.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> toMutable(e.getValue()),
                (k1, k2) -> k1,
                java.util.HashMap::new
            ));
      }
    }
    return (T) toMutable(input);
  }


  /**
   * Converts the provided object to its mutable representation, if applicable.
   * <p>
   * The method determines the type of the input object and applies the appropriate logic to convert
   * it into a mutable form: - If the object implements the {@code Mutable} interface, it is
   * returned as-is. - If the object implements the {@code Immutable} interface, the
   * {@code toMutable} method of the object is invoked to convert it. - If the object is a
   * {@code Collection}, it is converted to a mutable collection. - If the object is a {@code Map},
   * it is converted to a mutable map. - For all other objects that are not mutable or immutable,
   * the input is returned as-is.
   * <p>
   * This method ensures that compatible mutable versions of collections, maps, and other objects
   * are generated as needed.
   *
   * @param <T> the expected type of the mutable object
   * @param obj the input object to be converted to a mutable representation; can be an object,
   *            collection, map, or other type
   * @return the mutable version of the input object, or the input object itself if no conversion is
   * necessary
   */
  @SuppressWarnings("unchecked")
  public static <T> T toMutable(Object obj) {
    if (obj == null) {
      return null;
    }

    if (obj instanceof Mutable) {
      return (T) obj;
    } else if (obj instanceof Immutable<?>) {
      return (T) ((Immutable<?>) obj).toMutable();
    } else if (obj instanceof Collection<?>) {
      return toMutableCollection(obj);
    } else if (obj instanceof Map<?, ?>) {
      return toMutableMap(obj);
    } else {
      return (T) obj;
    }
  }

}
