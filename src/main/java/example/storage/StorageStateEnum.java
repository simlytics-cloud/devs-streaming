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

package example.storage;

/**
 * Defines an enumeration representing the various states of storage.
 * <p>
 * Each state corresponds to a specific numerical value, which can be used to uniquely identify and
 * differentiate between the storage states.
 * <p>
 * The available states are: - S0: Represents the storage state with a value of 0.0. - S1:
 * Represents the storage state with a value of 1.0. - S0_1: Represents the storage state with a
 * fractional value of 0.1.
 */
public enum StorageStateEnum {

  S0(0d), S1(1d), S0_1(0.1);

  StorageStateEnum(double value) {
  }
}
