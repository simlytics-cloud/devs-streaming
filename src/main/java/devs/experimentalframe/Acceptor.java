package devs.experimentalframe;

import devs.PDEVSModel;
import devs.msg.Bag;
import devs.msg.time.SimTime;

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
