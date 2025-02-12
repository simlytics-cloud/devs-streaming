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

package devs.experimentalframe;

import devs.Port;
import devs.msg.PortValue;
import devs.msg.time.LongSimTime;
import devs.utils.Schedule;
import java.util.HashMap;
import java.util.Map;

/**
 * PowerOfTwoGenerator is a concrete implementation of the Generator class, providing a predefined
 * schedule that emits events on two ports: "numbers" and "words." This generator produces events
 * corresponding to powers of two, with their numerical and string representations.
 * <p>
 * The generated schedule includes the following time-value pairs: - At time 1: Emits the number 1
 * on the "numbers" port and the string "One" on the "words" port. - At time 2: Emits the number 2
 * on the "numbers" port and the string "Two" on the "words" port. - At time 4: Emits the number 4
 * on the "numbers" port and the string "Four" on the "words" port. - At time 8: Emits the number 8
 * on the "numbers" port and the string "Eight" on the "words" port.
 * <p>
 * This class defines two output ports: - "numbers": Emits integers representing powers of two. The
 * events have a type of Integer. - "words": Emits strings corresponding to the textual
 * representation of the numbers. The events have a type of String.
 * <p>
 * This generator operates solely based on its internal schedule and does not process any external
 * events. The schedule is initialized during instance creation.
 */
public class PowerOfTwoGenerator extends Generator<LongSimTime> {

  public static final String MODEL_ID = "powerOfTwoGenerator";

  /**
   * Constructs a PowerOfTwoGenerator instance.
   * <p>
   * This constructor initializes the PowerOfTwoGenerator by invoking the superclass constructor
   * with: - A predefined model identifier specific to this generator ("powerOfTwoGenerator"). - A
   * schedule generated by the internal buildSchedule method.
   * <p>
   * The schedule defines the events that this generator will emit on its ports at specific times.
   * These events correspond to powers of two, including both their numerical and textual
   * representations.
   * <p>
   * This generator does not process external events and operates solely based on the internally
   * defined schedule, which is established at creation time.
   */
  public PowerOfTwoGenerator() {
    super(MODEL_ID, buildSchedule());

  }

  /**
   * Builds and returns an initial schedule of events containing predefined simulation times and
   * port values. The schedule defines a series of events, each associated with a specific time and
   * paired values for numerical and textual representations of powers of two.
   *
   * @return the constructed schedule containing events mapped to simulation times
   */
  protected static Schedule<LongSimTime> buildSchedule() {
    LongSimTime t1 = LongSimTime.builder().t(1L).build();
    LongSimTime t2 = LongSimTime.builder().t(2L).build();
    LongSimTime t4 = LongSimTime.builder().t(4L).build();
    LongSimTime t8 = LongSimTime.builder().t(8L).build();
    Schedule<LongSimTime> initialSchedule = new Schedule<>();

    initialSchedule.add(t1, new PortValue<Integer>(Integer.valueOf(1), "numbers"));
    initialSchedule.add(t1, new PortValue<String>("One", "words"));

    initialSchedule.add(t2, new PortValue<Integer>(Integer.valueOf(2), "numbers"));
    initialSchedule.add(t2, new PortValue<String>("Two", "words"));

    initialSchedule.add(t4, new PortValue<Integer>(Integer.valueOf(4), "numbers"));
    initialSchedule.add(t4, new PortValue<String>("Four", "words"));

    initialSchedule.add(t8, new PortValue<Integer>(Integer.valueOf(8), "numbers"));
    initialSchedule.add(t8, new PortValue<String>("Eight", "words"));

    return initialSchedule;
  }

  /**
   * Builds and returns a mapping of ports for the PowerOfTwoGenerator model.
   * <p>
   * The ports include: - "numbers": A port for exchanging integer-based numerical data. - "words":
   * A port for exchanging string-based text data.
   *
   * @return a map of port identifiers to their respective port objects, where the keys are the
   * unique port names and the values are instances of the corresponding port types.
   */
  @Override
  protected Map<String, Port<?>> buildPorts() {
    Map<String, Port<?>> ports = new HashMap<>();
    ports.put("numbers", new Port<Integer>("numbers", Integer.class));
    ports.put("words", new Port<String>("words", String.class));
    return ports;
  }

}
