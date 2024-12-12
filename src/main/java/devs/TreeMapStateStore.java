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

package devs;

import devs.msg.time.SimTime;
import java.util.TreeMap;

/**
 * Stores state values over time implemented as a TreeMap.

 * @param <T> the time type used by the StateStore
 * @param <V> the value type used by the
 */
public class TreeMapStateStore<T extends SimTime, V> implements StateStore<T, V> {

  final TreeMap<T, V> stateStore;

  public TreeMapStateStore(TreeMap<T, V> initialState) {
    this.stateStore = initialState;
  }

  @Override
  public V get(T time) {
    return stateStore.get(time);
  }

  @Override
  public void put(T time, V value) {
    stateStore.put(time, value);
  }

  /**
   * Returns the latest (current) value of this state variable.
   */
  @Override
  public V getLatest() {
    return stateStore.lastEntry().getValue();
  }
}
