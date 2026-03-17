/*
 * DEVS Streaming Framework Java Copyright (C) 2024 simlytics.cloud LLC and
 * DEVS Streaming Framework Java contributors.  All rights reserved.
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
 *
 */

package devs.proxy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import devs.iso.time.SimTime;

/**
 * KafkaSimTimeKey is a generic key class used for message identification based on simulation time
 * and an additional index within a Kafka-based simulation framework.
 * <p>
 * The purpose of this class is to uniquely identify messages or data points exchanged during
 * simulation by representing a combination of simulation time and an index parameter. The
 * simulation time is represented by the {@link SimTime} class, while the index is a {@code Long}.
 * <p>
 * Generic Parameters: - T: Represents the type of simulation time. It must extend the
 * {@link SimTime} abstract class, ensuring compatibility with various time representations.
 * <p>
 * Key Features: - Encapsulation of a {@code SimTime} instance, which provides simulation timing
 * data. - An index parameter, used as an additional identifier, enabling distinction of messages
 * with the same simulation time. - Lightweight class with getter methods for accessing its
 * contents.
 * <p>
 * Constructor: - Instantiated via a public constructor with both the {@code simTime} and
 * {@code index} arguments. - Compatible with Jackson's JSON deserialization via annotations like
 * {@code @JsonCreator} and {@code @JsonProperty}.
 * <p>
 * Applicability: - This class is typically used as part of a Kafka-based simulation system,
 * contributing to simulation messaging and identification mechanisms.
 */
public class KafkaSimTimeKey<T extends SimTime> {

  private final SimTime simTime;
  private final Long index;

  /**
   * Constructor for the KafkaSimTimeKey class that initializes an object with a specific simulation
   * time and an index value, used for uniquely identifying messages or data points within a
   * simulation.
   *
   * @param simTime The simulation time instance, representing a specific point in time within the
   *                simulation.
   * @param index   A Long value representing the index associated with the simulation time, used to
   *                further uniquely identify the key within the scope of the same simulation time.
   */
  @JsonCreator
  public KafkaSimTimeKey(@JsonProperty("simTime") SimTime simTime,
                         @JsonProperty("index") Long index) {
    this.simTime = simTime;
    this.index = index;
  }

  public SimTime getSimTime() {
    return simTime;
  }

  public Long getIndex() {
    return index;
  }
}
