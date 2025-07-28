package devs;

import java.util.List;

import devs.msg.Bag;
import devs.msg.PortValue;
import devs.msg.time.SimTime;

public interface PendingOutput<T extends SimTime, S> extends PDevsInterface<T, S> {

    public List<PortValue<?>> getPendingOutput();

    public void clearPendingOutput();

    public boolean hasPendingOutput();

    public void pendingInternalStateTransitionFunction(T currentTime);

    public void pendingConfluentStateTransitionFunction(T currentTime, Bag bag);

    @Override
    default void confluentStateTransitionFunction(T currentTime, Bag bag) {
        clearPendingOutput();
        pendingConfluentStateTransitionFunction(currentTime, bag);
    }

    @Override
    default void internalStateTransitionFunction(T currentTime) {
        clearPendingOutput();
        pendingInternalStateTransitionFunction(currentTime);
    }
    

    @Override
    default public Bag outputFunction() {
        Bag.Builder bagBuilder = Bag.builder();
        bagBuilder.addAllPortValueList(getPendingOutput());
        return bagBuilder.build();
    }



}
