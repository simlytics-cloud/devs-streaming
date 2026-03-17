package devs;

import devs.iso.PortValue;
import devs.iso.time.SimTime;
import java.util.List;

/**
 * Interface for a parallel DEVS (P-DEVS) model.
 * @param <T> the time type
 * @param <S> the state type
 */
public interface PDevsInterface<T extends SimTime, S> {


  /**
   * The DEVS internal state transition function. This function is called when the model is
   * executing a schedule internal transition that occurs with the passage of time
   *
   */
  public abstract void internalStateTransitionFunction();

  /**
   * The DEVS external state transition function. This function is called when an external event
   * arrives at one of the model's input ports. Note two important considerations. First, the type
   * of event is not known in advance, so this function will have to use the {@link devs.iso.PortValue
   * PortValue} contained in the input list to identify the type of event and
   * process it appropriately. In addition, more than one input is possible at an intant in time.
   *
   * @param elapsedTime the time since the last transition
   * @param inputs the list of inputs that has arrived at the current time
   */
  public void externalStateTransitionFunction(T elapsedTime, List<PortValue<?>> inputs);

  /**
   * The parallel DEVS (PDEVS) confluent transition function. This function is called if an external
   * event arrives at the same time the DEVS model is also sheculed for an internal state
   * transition. A trivial implementation could call the external state transition function, then
   * the internal state transition function. In other cases, the order may depend on the type of
   * event received.
   *
   * @param inputs the list of inputs that has arrived at the current time
   */
  public void confluentStateTransitionFunction(List<PortValue<?>> inputs);

  /**
   * This is the DEVS time advance function. The DEVS simulator calls this method to determine the
   * time of this model's next internal state transition.
   *
   * @return the time of the next scheduled internal state transition
   */
  public T timeAdvanceFunction();

  /**
   * The DEVS output function. The simulator calls this function immediately prior to an internal
   * state transition. 
   *
   * @return the list of outputs
   */
  public List<PortValue<?>> outputFunction();


}
