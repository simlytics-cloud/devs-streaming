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

package devs.msg.state;

/**
 * An interface for objects whose state can be updated a subsequent state update.
 * 
 * @param <T> the type of state object
 */
public interface SimState<T> {

  /**
   * An object of type T will be updated by another object of type T, the stateUpdate.
   * 
   * @param stateUpdate this object will only have the fields that have changed since the previous
   *        update set.
   * @return a new object of type T whose fields have been updated based on the values in the state
   *         update.
   */
  T updateState(T stateUpdate);
}
