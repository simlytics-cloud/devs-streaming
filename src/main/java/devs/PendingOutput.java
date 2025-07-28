package devs;

import java.util.List;

import devs.msg.Bag;
import devs.msg.PortValue;
import devs.msg.time.SimTime;

/**
 * The PendingOutput interface extends the PDevsInterface and provides functionality related
 * to handling pending outputs in a DEVS (Discrete Event System Specification) simulation model.
 * It defines additional methods to manage and process outputs that are generated but not yet
 * emitted by the model.
 *
 * @param <T> the type of the simulation time, extending the SimTime class
 * @param <S> the type representing the model's state
 */
public interface PendingOutput<T extends SimTime, S> extends PDevsInterface<T, S> {

    /**
     * Retrieves a list of pending outputs for the DEVS model.
     * Each pending output is encapsulated in a {@code PortValue} object,
     * which includes the port identifier and associated value.
     * The returned list contains outputs that were generated but have
     * not yet been emitted by the model.
     *
     * @return a list of {@code PortValue<?>} representing the pending outputs.
     */
    public List<PortValue<?>> getPendingOutput();

    /**
     * Clears all pending outputs in the DEVS model. This method is responsible for
     * resetting any outputs that were generated but not yet emitted by the model,
     * ensuring that the pending output list is empty.
     */
    public void clearPendingOutput();

    /**
     * Determines whether there are pending outputs in the DEVS model.
     * This method checks if there are any outputs that have been generated
     * but not yet emitted.
     *
     * @return {@code true} if there are pending outputs; {@code false} otherwise
     */
    public boolean hasPendingOutput();

    /**
     * This method defines the behavior for handling an internal state transition
     * during a simulation when the model has pending outputs. It is invoked as part
     * of the DEVS simulation lifecycle to transition the internal state of the model
     * at a given simulation time.
     *
     * @param currentTime the current simulation time at which the internal state
     *                    transition occurs
     */
    public void pendingInternalStateTransitionFunction(T currentTime);

    /**
     * Handles the confluent state transition function when there are pending outputs in the DEVS model.
     * This method is invoked as part of the DEVS simulation lifecycle and defines the behavior of the
     * model during a confluent transition, which occurs when external and internal events coincide
     * at the same simulation time.
     *
     * @param currentTime the current simulation time at which the confluent state transition occurs
     * @param bag         the set of external inputs provided to the model during the confluent transition
     */
    public void pendingConfluentStateTransitionFunction(T currentTime, Bag bag);

    /**
     * Handles the confluent state transition function as part of the DEVS model's simulation lifecycle.
     * This method is responsible for clearing pending outputs and invoking the pending confluent
     * state transition logic when external and internal events coincide at the same simulation time.
     *
     * @param currentTime the current simulation time at which the confluent state transition occurs
     * @param bag         the set of external inputs provided to the model during the confluent transition
     */
    @Override
    default void confluentStateTransitionFunction(T currentTime, Bag bag) {
        clearPendingOutput();
        pendingConfluentStateTransitionFunction(currentTime, bag);
    }

    /**
     * Handles the internal state transition function for the DEVS model. This method is invoked
     * during the simulation lifecycle to process internal transitions when no external inputs are
     * present. It ensures that any pending outputs in the model are cleared before invoking the
     * specific behavior for handling the internal state transition.
     *
     * @param currentTime the current simulation time at which the internal state transition occurs
     */
    @Override
    default void internalStateTransitionFunction(T currentTime) {
        clearPendingOutput();
        pendingInternalStateTransitionFunction(currentTime);
    }
    

    /**
     * Constructs and returns the current pending outputs of the DEVS model
     * encapsulated in a {@code Bag} object.
     * The method retrieves all pending outputs by utilizing the list provided
     * by {@code getPendingOutput()}, collecting them into a {@code Bag} using
     * a {@code Bag.Builder}, and returning the resulting {@code Bag}.
     *
     * @return a {@code Bag} containing all pending outputs of the DEVS model.
     */
    @Override
    default public Bag outputFunction() {
        Bag.Builder bagBuilder = Bag.builder();
        bagBuilder.addAllPortValueList(getPendingOutput());
        return bagBuilder.build();
    }



}
