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

import devs.msg.time.SimTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;


/**
 * Schedule is a specialized TreeMap designed to store and manage events associated with specific
 * time points. The keys represent time points that extend the SimTime abstraction, while the values
 * are lists of events associated with those times.
 * <p>
 * This class provides functionality to add new events to specific time points and retrieve all
 * scheduled time-event pairs in a human-readable format.
 *
 * @param <T> The type of time points used, which must extend the SimTime class.
 */
public class Schedule<T extends SimTime> extends TreeMap<T, ArrayList<Object>> {

  /**
   * Default constructor for the Schedule class. Initializes an empty Schedule object that extends
   * the TreeMap data structure, allowing mapping between time points and associated event lists.
   * <p>
   * The keys of the schedule are expected to extend the SimTime abstraction, and the values are
   * lists of events (as Objects) associated with each time point.
   */
  public Schedule() {
  }

  /**
   * Constructs a new Schedule object with the contents of the specified map. The keys and values of
   * the provided map are used to initialize this Schedule.
   *
   * @param m the map whose mappings are to be placed in this Schedule. The keys must extend the
   *          SimTime class, and the values must be lists of events.
   */
  public Schedule(Map<? extends T, ArrayList<Object>> m) {
    super(m);
  }

  /**
   * Constructs a new Schedule object with the contents of the specified sorted map. The keys and
   * values of the provided sorted map are used to initialize this Schedule.
   *
   * @param m the sorted map whose mappings are to be placed in this Schedule. The keys must extend
   *          the SimTime class, and the values must be lists of events.
   */
  public Schedule(SortedMap<T, ArrayList<Object>> m) {
    super(m);
  }

  /**
   * Adds an event to the schedule at the specified time. If the time point already exists in the
   * schedule, the event is appended to the list of events at that time. If the time point does not
   * exist, a new entry is created with the given time and event.
   *
   * @param time  the time point at which the event should be scheduled. It must be of type T, which
   *              extends the SimTime class.
   * @param event the event to be associated with the specified time point.
   */
  public void add(T time, Object event) {
    if (this.containsKey(time)) {
      this.get(time).add(event);
    } else {
      ArrayList<Object> events = new ArrayList<>();
      events.add(event);
      this.put(time, events);
    }
  }

  /**
   * Generates a string representation of the Schedule object, displaying the scheduled time points
   * and their associated events in a structured format.
   *
   * @return a string representation of the schedule, where each time point and its associated list
   * of events are formatted for readability. Each time point is shown on a new line, followed by
   * its corresponding events indented below it.
   */
  @Override
  public String toString() {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("Scheduled Events: \n");
    for (T key : keySet()) {
      stringBuilder.append("  Time:").append(key).append("\n");
      for (Object event : get(key)) {
        stringBuilder.append("    Event:").append(event).append("\n");
      }
    }
    return stringBuilder.toString();
  }
}
