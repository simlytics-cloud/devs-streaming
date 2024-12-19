package devs;

import devs.msg.Bag;
import devs.msg.PortValue;
import devs.msg.time.SimTime;
import devs.utils.Schedule;
import java.util.ArrayList;
import java.util.List;
import scala.collection.mutable.StringBuilder;

/**
 * An abstract base class for creating models in the PDEVS (Parallel Discrete Event System)
 * framework that require scheduled event handling. The class facilitates the management of a
 * schedule that maintains a list of events and their corresponding simulation times.
 *
 * @param <T> The type representing simulation time, extending the SimTime class.
 * @param <S> The type representing the state of the model.
 */
public abstract class ScheduledDevsModel<T extends SimTime, S> extends PDEVSModel<T, S> {

  protected final Schedule<T> schedule;

  /**
   * Constructs a new instance of the ScheduledDevsModel class.
   *
   * @param modelState      the initial state of the DEVS model
   * @param modelIdentifier a unique identifier for this model instance
   * @param schedule        the schedule associated with the DEVS model, used for event scheduling
   *                        and management
   */
  public ScheduledDevsModel(S modelState, String modelIdentifier, Schedule<T> schedule) {
    super(modelState, modelIdentifier);
    this.schedule = schedule;
  }


  /**
   * Determines the next scheduled internal state transition time for the model. If the schedule is
   * empty, the method returns the maximum time value (indicating no further transitions).
   * Otherwise, it returns the next scheduled time from the internal schedule.
   *
   * @param currentTime the current time of the simulator
   * @return the time of the next scheduled internal state transition, or the maximum time value if
   * the schedule is empty
   */
  @Override
  public T timeAdvanceFunction(T currentTime) {
    StringBuilder stringBuilder = new StringBuilder();
    T nextTime = null;
    if (schedule.isEmpty()) {
      nextTime = (T) currentTime.getMaxValue();
    } else {
      nextTime = schedule.firstKey();
    }
    stringBuilder.append("Schedule at " + currentTime + " is " + schedule);
    stringBuilder.append("Time advance function at " + currentTime + " is " + nextTime);
    logger.debug(stringBuilder.toString());
    return nextTime;
  }

  /**
   * Checks if there is any pending output in the current state of the schedule.
   * <p>
   * The method iterates through the first entry in the schedule. If an event of type
   * {@code PortValue<?>} is found, it indicates that there is pending output. The method logs the
   * current state of the schedule and whether a pending output is detected before returning.
   *
   * @return {@code true} if there is pending output based on the current schedule, {@code false}
   * otherwise
   */
  protected boolean hasPendingOutput() {
    StringBuilder stringBuilder = new StringBuilder();
    boolean hasOutput = false;
    if (!schedule.isEmpty()) {
      for (Object event : schedule.firstEntry().getValue()) {
        if (event instanceof PortValue<?>) {
          hasOutput = true;
          break;
        }
      }
    }
    stringBuilder.append("In has pending output chedule is " + schedule);
    stringBuilder.append("Has penidng output is " + hasOutput);
    logger.debug(stringBuilder.toString());
    return false;
  }

  /**
   * Retrieves a list of pending outputs from the current schedule.
   * <p>
   * The method checks the first entry in the schedule and extracts events of type
   * {@code PortValue<?>}. These extracted events are considered as pending outputs. Additionally,
   * it logs the current state of the schedule and the pending outputs for debugging purposes.
   *
   * @return a list of {@code PortValue<?>} representing the pending outputs from the schedule. If
   * the schedule is empty, an empty list is returned.
   */
  protected List<PortValue<?>> getPendingOutput() {
    StringBuilder stringBuilder = new StringBuilder();
    List<PortValue<?>> pendingOutputs = new ArrayList<>();
    if (!schedule.isEmpty()) {
      for (Object event : schedule.firstEntry().getValue()) {
        if (event instanceof PortValue<?> pv) {
          pendingOutputs.add(pv);
        }
      }
    }
    stringBuilder.append("In get pending output schedule is " + schedule);
    stringBuilder.append("Penging output is:\n");
    for (PortValue<?> pv : pendingOutputs) {
      stringBuilder.append("  " + pv);
    }
    logger.debug(stringBuilder.toString());
    return pendingOutputs;
  }

  /**
   * Clears any pending output events from the schedule associated with the DEVS model.
   * <p>
   * This method checks the current schedule for any pending events and removes those classified as
   * {@code PortValue<?>}. If the first scheduled event list becomes empty after removing all
   * {@code PortValue<?>} entries, the corresponding key is also removed from the schedule.
   * Debugging information is logged both before and after the schedule is updated, providing
   * insights into the clearing process.
   * <p>
   * The method ensures that only non-output related events remain in the schedule, maintaining
   * consistency in the DEVS model's state transitions.
   */
  protected void clearPendingOutput() {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append("Prior to clearing output schedule is " + schedule);
    if (!schedule.isEmpty()) {
      ArrayList<Object> currentEvents = schedule.firstEntry().getValue();
      ArrayList<Object> portValues = new ArrayList<>();
      // Find all the port values
      for (Object event : currentEvents) {
        if (event instanceof PortValue<?> pv) {
          portValues.add(pv);
        }
      }
      // Remove port values from schedule
      for (Object pv : portValues) {
        currentEvents.remove(pv);
      }
      if (currentEvents.isEmpty()) {
        schedule.remove(schedule.firstKey());
      } else {
        schedule.put(schedule.firstKey(), currentEvents);
      }
    }
    stringBuilder.append("After clearing output schedule is " + schedule);
    logger.debug(stringBuilder.toString());
  }

  /**
   * Generates the output of the DEVS model by collecting all pending output events from the
   * schedule. The pending outputs are aggregated into a Bag object for further processing or
   * dispatching.
   *
   * @return a Bag containing all pending output events collected from the current schedule. If
   * there are no pending outputs, an empty Bag is returned.
   */
  @Override
  public Bag outputFunction() {
    Bag.Builder bagBuilder = Bag.builder();
    bagBuilder.addAllPortValueList(getPendingOutput());
    // clearPendingOutput();
    return bagBuilder.build();
  }


}
