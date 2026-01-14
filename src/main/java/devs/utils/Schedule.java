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

package devs.utils;

import devs.Port;
import devs.iso.PortValue;
import devs.iso.time.SimTime;
import java.util.ArrayList;
import java.util.List;
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
public class Schedule<T extends SimTime> {
  
  protected interface Event {}
  
  public static class ScheduledEvent implements Event {
    protected final Object event;

    protected ScheduledEvent(Object event) {
      if (event instanceof PortValue<?>) {
        throw new IllegalArgumentException("Cannot schedule port value event." 
            + "Must use OutputEvent instead.");
      }
      this.event = event;
    }
    
    protected Object getEvent() {
      return event;
    }
  }
  
  protected static class OutputEvent implements Event {
    protected final PortValue<?> portValue;
    protected OutputEvent(PortValue<?> portValue) {
      this.portValue = portValue;
    }
    
    protected PortValue<?> getPortValue() {
      return portValue;
    }
    
  }
  
  protected TreeMap<T, ArrayList<Event>> schedule = new TreeMap<T, ArrayList<Event>>();

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
   * Adds an event to the schedule at the specified time. If the time point already exists in the
   * schedule, the event is appended to the list of events at that time. If the time point does not
   * exist, a new entry is created with the given time and event.
   *
   * @param time  the time point at which the event should be scheduled. It must be of type T, which
   *              extends the SimTime class.
   * @param event the event to be associated with the specified time point.
   */
  public void scheduleInternalEvent(T time, Object event) {
    if (event instanceof PortValue<?> portValue) {
      scheduleOutputEvent(time, portValue);
    }
    ScheduledEvent scheduledEvent = new ScheduledEvent(event);
    if (schedule.containsKey(time)) {
      schedule.get(time).add(scheduledEvent);
    } else {
      ArrayList<Event> events = new ArrayList<>();
      events.add(scheduledEvent);
      schedule.put(time, events);
    }
  }
  
  public void scheduleOutputEvent(T time, PortValue<?> portValue) {
    OutputEvent outputEvent = new OutputEvent(portValue);
    if (schedule.containsKey(time)) {
      schedule.get(time).add(outputEvent);
    } else {
      ArrayList<Event> events = new ArrayList<>();
      events.add(outputEvent);
      schedule.put(time, events);
    }
  }

  public <P> void scheduleOutput(T scheduledTime, Port<P> port, P value) {
    this.scheduleOutputEvent(scheduledTime, port.createPortValue(value));
  }
  
  public T getFirstEventTime() {
    return schedule.firstKey();
  }
  
  public boolean isEmpty() {
    return schedule.isEmpty();
  }
  
  
  public boolean hasCurrentOutput(T currentTime) {
    List<Event> events = schedule.get(currentTime);
    for (Event e : events) {
      if (e instanceof OutputEvent) {
        return true;
      }
    }
    return false;
  }

  public ArrayList<Object> removeCurrentScheduledEvents(T currentTime) {
    if (schedule.get(currentTime) == null) {
      return new ArrayList<>();
    }
    ArrayList<Event> allEvents = schedule.remove(currentTime);
    ArrayList<ScheduledEvent> scheduledEvents = new ArrayList<>();
    ArrayList<Event> remainingEvents = new ArrayList<>();

    for (Event event : allEvents) {
      if (event instanceof ScheduledEvent scheduledEvent) {
        scheduledEvents.add(scheduledEvent);
      } else {
        remainingEvents.add(event);
      }
    }

    if (!remainingEvents.isEmpty()) {
      schedule.put(currentTime, remainingEvents);
    }

    ArrayList<Object> events = new ArrayList<>();
    for (ScheduledEvent scheduledEvent : scheduledEvents) {
      events.add(scheduledEvent.getEvent());
    }
    return events;
  }
  

  public void removeCurrentScheduledOutput(T currentTime) {
    if (schedule.get(currentTime) != null) {
      ArrayList<Event> allEvents = new ArrayList<>(schedule.remove(currentTime));
      allEvents.removeIf(event -> event instanceof OutputEvent outputEvent);
      if (!allEvents.isEmpty()) {
        schedule.put(currentTime, allEvents);
      }
    }
  }

  public ArrayList<PortValue<?>> getCurrentScheduledOutput() {
    if (schedule.isEmpty()) {
      throw new IllegalArgumentException("Called getCurrentScheduledOutput on empty schedule.");
    }
    List<OutputEvent> scheduledOutput = new ArrayList<>();
    for (Event event : schedule.get(schedule.firstKey())) {
      if (event instanceof OutputEvent outputEvent) {
        scheduledOutput.add(outputEvent);
      }
    }
    ArrayList<PortValue<?>> portValues = new ArrayList<>();
    for (OutputEvent outputEvent : scheduledOutput) {
      portValues.add(outputEvent.getPortValue());
    }
    return portValues;
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
    for (T key : schedule.keySet()) {
      stringBuilder.append("  Time:").append(key).append("\n");
      for (Object event : schedule.get(key)) {
        stringBuilder.append("    Event:").append(event).append("\n");
      }
    }
    return stringBuilder.toString();
  }
}
