package devs.experimentalframe;

import devs.PDEVSModel;
import devs.msg.Bag;
import devs.msg.time.SimTime;

/**
 * The Acceptor class is an abstract DEVS (Discrete Event System Specification) model that
 * represents a model processing incoming events without generating any output events.  Users of
 * this class should override the externalStateTransitionFunction to validate that the incoming
 * messages are correct.
 *
 * @param <T> the time type used by the model, extending the SimTime class
 * @param <S> the internal state type of the model
 */
public abstract class Acceptor<T extends SimTime, S> extends PDEVSModel<T, S> {

  public static String modelIdentifier = "Acceptor";

  public Acceptor(S initialState) {
    super(initialState, modelIdentifier);
  }

  @Override
  public void internalStateTransitionFunction(T currentTime) {
    // Nothing to do

  }


  @Override
  public void confluentStateTransitionFunction(T currentTime, Bag bag) {
    externalStateTransitionFunction(currentTime, bag);
    internalStateTransitionFunction(currentTime);

  }

  @Override
  public T timeAdvanceFunction(T currentTime) {
    return (T) currentTime.getMaxValue();
  }

  @Override
  public Bag outputFunction() {
    // No output from Acceptor
    return Bag.builder().build();
  }

}
