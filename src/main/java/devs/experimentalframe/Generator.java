package devs.experimentalframe;

import devs.Port;
import devs.ScheduledDevsModel;
import devs.msg.Bag;
import devs.msg.time.SimTime;
import devs.utils.Schedule;
import java.util.Map;


public abstract class Generator<T extends SimTime> extends ScheduledDevsModel<T, Void> {


  public static final String MODEL_ID = "Generator";
  protected final Map<String, Port<?>> ports;

  protected Generator(String modelIdentifier, Schedule<T> schedule) {
    super(null, modelIdentifier, schedule);
    this.ports = buildPorts();
  }


  protected abstract Map<String, Port<?>> buildPorts();


  @Override
  public void internalStateTransitionFunction(T currentTime) {
    clearPendingOutput();

  }

  @Override
  public void externalStateTransitionFunction(T currentTime, Bag bag) {
    // No external events
    throw new IllegalArgumentException("Generator does not expect external events.  \n"
        + "Got event with port identifier of "
        + bag.getPortValueList().get(0).getPortIdentifier());

  }

  @Override
  public void confluentStateTransitionFunction(T currentTime, Bag bag) {
    externalStateTransitionFunction(currentTime, bag); // Will throw an error. No external
    // events expected
    internalStateTransitionFunction(currentTime);
  }


  public Map<String, Port<?>> getPorts() {
    return ports;
  }


}
