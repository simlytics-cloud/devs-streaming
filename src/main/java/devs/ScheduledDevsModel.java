package devs;

import devs.msg.Bag;
import devs.msg.PortValue;
import devs.msg.time.SimTime;
import devs.utils.Schedule;
import java.util.ArrayList;
import java.util.List;
import scala.collection.mutable.StringBuilder;

public abstract class ScheduledDevsModel<T extends SimTime, S> extends PDEVSModel<T, S> {

  protected final Schedule<T> schedule;

  public ScheduledDevsModel(S modelState, String modelIdentifier, Schedule<T> schedule) {
    super(modelState, modelIdentifier);
    this.schedule = schedule;
  }


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

  @Override
  public Bag outputFunction() {
    Bag.Builder bagBuilder = Bag.builder();
    bagBuilder.addAllPortValueList(getPendingOutput());
    // clearPendingOutput();
    return bagBuilder.build();
  }


}
