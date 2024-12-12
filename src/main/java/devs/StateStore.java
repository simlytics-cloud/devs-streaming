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

/**
 * Interface of a class to store state values over time.

 * @param <T> the time type used by the StateStore
 * @param <V> the value type used by the StateStore
 */
public interface StateStore<T extends SimTime, V> {

  abstract V get(T time);

  abstract void put(T time, V value);

  abstract V getLatest();
}
