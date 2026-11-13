package devs.configuration;

import devs.iso.time.SimTime;
import java.util.Objects;

/**
 * An atomic model's properties and initial state, without constructing a running model.
 *
 * <p>Consistency between {@code initialState} and {@code initializationTime} depends on the model's
 * state type and is checked by the model-specific configuration and persistence code, not here.
 *
 * @param <T> the native simulation time type of this model
 * @param <P> the immutable properties type
 * @param <S> the initial state type
 */
public record AtomicNode<T extends SimTime, P, S>(
        String identifier, String modelType, T initializationTime, P properties, S initialState)
        implements ModelNode<T> {

    public AtomicNode {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(modelType, "modelType");
        Objects.requireNonNull(initializationTime, "initializationTime");
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(initialState, "initialState");
    }
}
