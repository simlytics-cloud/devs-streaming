package devs.configuration;

import devs.iso.time.SimTime;

/**
 * A configured subordinate model, identified within its containing coupled model.
 *
 * <p>Nodes are data only: they describe prepared properties, initial states and structure without
 * constructing running models. {@code T} is the model's native clock; nodes with different native
 * clocks may appear in one tree.
 *
 * @param <T> the native simulation time type of this model
 */
public sealed interface ModelNode<T extends SimTime> permits AtomicNode, CoupledNode {

    /** The configuration node schema version written and accepted by this library. */
    int SCHEMA_VERSION = 1;

    /** Identifier of this model within its containing coupled model. */
    String identifier();

    /** Identifier of the model definition this node configures. */
    String modelType();

    /** Initialization time in this model's native clock. */
    T initializationTime();

    /** Schema version of this node. */
    default int schemaVersion() {
        return SCHEMA_VERSION;
    }

    /** {@code "atomic"} or {@code "coupled"}. */
    default String kind() {
        return this instanceof AtomicNode<?, ?, ?> ? "atomic" : "coupled";
    }
}
