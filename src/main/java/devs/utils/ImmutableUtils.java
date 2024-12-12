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

public class ImmutableUtils {

  public static <T> List<T> addOne(List<T> list, T item) {
    List<T> copy = new ArrayList<>(list.stream().toList());
    copy.add(item);
    return copy.stream().toList();
  }

  public static <T> List<T> addAll(List<T> list, List<T> items) {
    List<T> copy = new ArrayList<>(list.stream().toList());
    copy.addAll(items);
    return copy.stream().toList();
  }

  public static <T> List<T> removeOne(List<T> list, T item) {
    List<T> copy = new ArrayList<>(list.stream().toList());
    copy.remove(item);
    return copy.stream().toList();
  }

  public static <T> List<T> removeAll(List<T> list, List<T> items) {
    List<T> copy = new ArrayList<>(list.stream().toList());
    copy.removeAll(items);
    return copy.stream().toList();
  }

  private ImmutableUtils() {
  }
}
