/*
 * DEVS Streaming Framework Copyright (C) 2023 simlytics.cloud LLC and DEVS Streaming Framework
 * contributors
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

package devs.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for performing operations on immutable lists in a functional and memory-safe
 * manner. This class provides methods to add or remove elements from lists without modifying the
 * original list, ensuring immutability by returning new list instances.
 * <p>
 * This class cannot be instantiated.
 */
public class ImmutableUtils {

  /**
   * Creates a new list by adding the specified item to the end of the given list. The original list
   * remains unchanged, ensuring immutability.
   *
   * @param <T>  the type of elements in the list
   * @param list the original list to which an item is to be added
   * @param item the item to be added to the list
   * @return a new list containing all elements of the original list and the added item
   */
  public static <T> List<T> addOne(List<T> list, T item) {
    List<T> copy = new ArrayList<>(list.stream().toList());
    copy.add(item);
    return copy.stream().toList();
  }

  /**
   * Creates a new list by adding all elements from a specified list of items to the end of the
   * given list. The original list and the items list remain unchanged, ensuring immutability by
   * returning a new list instance.
   *
   * @param <T>   the type of elements in the list
   * @param list  the original list to which items are to be added
   * @param items the list of items to be added to the original list
   * @return a new list containing all elements of the original list followed by all elements of the
   * items list
   */
  public static <T> List<T> addAll(List<T> list, List<T> items) {
    List<T> copy = new ArrayList<>(list.stream().toList());
    copy.addAll(items);
    return copy.stream().toList();
  }

  /**
   * Creates a new list by removing the first occurrence of the specified item from the given list.
   * The original list remains unchanged, ensuring immutability by returning a new list instance.
   *
   * @param <T>  the type of elements in the list
   * @param list the original list from which an item is to be removed
   * @param item the item to be removed from the list
   * @return a new list containing all elements of the original list except the first occurrence of
   * the specified item
   */
  public static <T> List<T> removeOne(List<T> list, T item) {
    List<T> copy = new ArrayList<>(list.stream().toList());
    copy.remove(item);
    return copy.stream().toList();
  }

  /**
   * Creates a new list by removing all elements from the given list that are present in the
   * specified list of items. The original list and the items list remain unchanged, ensuring
   * immutability by returning a new list instance.
   *
   * @param <T>   the type of elements in the list
   * @param list  the original list from which elements are to be removed
   * @param items the list of items to be removed from the original list
   * @return a new list containing all elements of the original list except those that are also
   * present in the specified items list
   */
  public static <T> List<T> removeAll(List<T> list, List<T> items) {
    List<T> copy = new ArrayList<>(list.stream().toList());
    copy.removeAll(items);
    return copy.stream().toList();
  }

  /**
   * A utility class containing methods for performing immutable operations on collections. This
   * class is designed to provide functionality for creating new immutable instances of collections
   * after performing add, remove, or similar operations, ensuring the original collections remain
   * unchanged.
   * <p>
   * This class cannot be instantiated.
   */
  private ImmutableUtils() {
  }
}
