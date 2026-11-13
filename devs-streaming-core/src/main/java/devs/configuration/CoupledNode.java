package devs.configuration;

import devs.iso.time.SimTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A coupled model's children, keyed by identifier in declaration order.
 *
 * <p>Every child is bound to exactly one role, and every bound identifier names a child. Children
 * may use native clocks that differ from this node's, so no equality between parent and child
 * initialization times is required. Both maps and the role lists are defensively copied, keep
 * their insertion order, and cannot be modified.
 *
 * @param <T> the native simulation time type of this coupled model
 */
public record CoupledNode<T extends SimTime>(
        String identifier,
        String modelType,
        T initializationTime,
        Map<String, List<String>> roleBindings,
        Map<String, ModelNode<?>> children)
        implements ModelNode<T> {

    public CoupledNode {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(modelType, "modelType");
        Objects.requireNonNull(initializationTime, "initializationTime");
        Objects.requireNonNull(roleBindings, "roleBindings");
        Objects.requireNonNull(children, "children");

        Map<String, ModelNode<?>> childCopy = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends ModelNode<?>> entry : children.entrySet()) {
            String id = Objects.requireNonNull(entry.getKey(), "child identifier");
            ModelNode<?> child = Objects.requireNonNull(entry.getValue(), "child");
            if (!id.equals(child.identifier())) {
                throw new IllegalArgumentException("Child identifier mismatch: " + id);
            }
            childCopy.put(id, child);
        }

        Map<String, List<String>> roles = new LinkedHashMap<>();
        Set<String> bound = new HashSet<>();
        for (Map.Entry<String, List<String>> binding : roleBindings.entrySet()) {
            String role = Objects.requireNonNull(binding.getKey(), "role");
            List<String> ids = List.copyOf(Objects.requireNonNull(binding.getValue(), "role identifiers"));
            for (String id : ids) {
                if (!childCopy.containsKey(id) || !bound.add(id)) {
                    throw new IllegalArgumentException("Invalid or duplicate child binding: " + id);
                }
            }
            roles.put(role, ids);
        }
        if (!bound.equals(childCopy.keySet())) {
            throw new IllegalArgumentException("Unbound children");
        }

        children = Collections.unmodifiableMap(childCopy);
        roleBindings = Collections.unmodifiableMap(roles);
    }
}