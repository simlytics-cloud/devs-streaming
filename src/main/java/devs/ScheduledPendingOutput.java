package devs;

import devs.msg.Bag;
import devs.msg.time.SimTime;

public interface ScheduledPendingOutput<T extends SimTime, S> extends PendingOutput<T, S>, ScheduledDevsModel<T, S> {

    @Override
    default void scheduledConfluentStateTransitionFunction(T currentTime, Bag bag) {
        pendingConfluentStateTransitionFunction(currentTime, bag);
    }

    
    @Override
    default void scheduledInternalStateTransitionFunction(T currentTime) {
        pendingInternalStateTransitionFunction(currentTime);
    }

    @Override
    default void confluentStateTransitionFunction(T currentTime, Bag bag) {
        clearPendingOutput();
        clearScheduledOutput();
        scheduledConfluentStateTransitionFunction(currentTime, bag);
    }

    @Override
    default void internalStateTransitionFunction(T currentTime) {
        clearPendingOutput();
        clearScheduledOutput();
        scheduledInternalStateTransitionFunction(currentTime);
    }

    @Override
    default Bag outputFunction() {
        Bag.Builder bagBuilder = Bag.builder();
        bagBuilder.addAllPortValueList(getPendingOutput());
        bagBuilder.addAllPortValueList(getScheduledOutput());
        return bagBuilder.build();
    }

}
