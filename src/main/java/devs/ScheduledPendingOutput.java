package devs;

import devs.msg.Bag;
import devs.msg.time.SimTime;

/**
 * The ScheduledPendingOutput interface extends the functionality of both PendingOutput and
 * ScheduledDevsModel. It combines the handling of pending and scheduled outputs within the context
 * of a DEVS (Discrete Event System Specification) simulation model.
 * <p>
 * This interface provides default behavior for managing the state transitions and output function
 * related to scheduled models with pending outputs. The implementation integrates the logic for
 * processing scheduled and pending outputs during simulation events.
 *
 * @param <T> the type representing the simulation time, extending the SimTime class
 * @param <S> the type representing the model's state
 */
public interface ScheduledPendingOutput<T extends SimTime, S> extends PendingOutput<T, S>,
    ScheduledDevsModel<T, S> {

  /**
   * Executes the scheduled confluent state transition function for the model. This method combines
   * the logic for handling scheduled and confluent state transitions within the simulation
   * framework.
   *
   * @param currentTime the current simulation time
   * @param bag         the input bag containing events or data to be processed
   */
  @Override
  default void scheduledConfluentStateTransitionFunction(T currentTime, Bag bag) {
    pendingConfluentStateTransitionFunction(currentTime, bag);
  }


  /**
   * Defines the behavior for executing a scheduled internal state transition function within the
   * context of a DEVS simulation model. This method provides a default implementation that
   * delegates the internal state transition to the {@code pendingInternalStateTransitionFunction},
   * ensuring that pending outputs are taken into account in the transition process.
   *
   * @param currentTime the current simulation time when the scheduled internal state transition is
   *                    executed
   */
  @Override
  default void scheduledInternalStateTransitionFunction(T currentTime) {
    pendingInternalStateTransitionFunction(currentTime);
  }

  /**
   * Executes the confluent state transition function in the context of the DEVS simulation model.
   * This method combines the logic of clearing any pending and scheduled outputs and then delegates
   * the state transition functionality to the scheduled confluent state transition function.
   *
   * @param currentTime the current simulation time
   * @param bag         the input bag containing events or data to be processed
   */
  @Override
  default void confluentStateTransitionFunction(T currentTime, Bag bag) {
    clearPendingOutput();
    clearScheduledOutput();
    scheduledConfluentStateTransitionFunction(currentTime, bag);
  }

  /**
   * Executes the internal state transition function for the DEVS model. This method is responsible
   * for clearing any pending and scheduled outputs before delegating the state transition logic to
   * the scheduled internal state transition function.
   *
   * @param currentTime the current simulation time when the internal state transition is executed
   */
  @Override
  default void internalStateTransitionFunction(T currentTime) {
    clearPendingOutput();
    clearScheduledOutput();
    scheduledInternalStateTransitionFunction(currentTime);
  }

  /**
   * Constructs the output bag by combining pending and scheduled output events. The method
   * retrieves and consolidates all output values from the pending and scheduled outputs to create a
   * unified output that aligns with the DEVS (Discrete Event System Specification) model's
   * semantics.
   *
   * @return a {@code Bag} containing the merged pending and scheduled output events.
   */
  @Override
  default Bag outputFunction() {
    Bag.Builder bagBuilder = Bag.builder();
    bagBuilder.addAllPortValueList(getPendingOutput());
    bagBuilder.addAllPortValueList(getScheduledOutput());
    return bagBuilder.build();
  }

}
