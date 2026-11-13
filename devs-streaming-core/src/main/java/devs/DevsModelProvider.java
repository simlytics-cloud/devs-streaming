package devs;

import devs.iso.DevsMessage;
import devs.iso.time.SimTime;
import devs.pekko.Actors;
import devs.utils.ModelUtils;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;

/**
 * Provides an atomic DEVS model, which is constructed at its initial time when its simulator is created.
 */
public interface DevsModelProvider<T extends SimTime> extends SimulatorProvider<T> {

    /**
     * Constructs the model at {@code initialTime}.
     *
     * @param initialTime the initial time of the simulation
     * @return the model, whose identifier should be {@link #getModelIdentifier()}
     */
    PDEVSModel<T, ?> createModel(T initialTime);

    /**
     * Constructs the model at {@code initialTime} and spawns its simulator, named for this provider's identifier.
     *
     * @param context the actor context
     * @param initialTime the initial time of the simulation
     * @return the DEVS Simulator executing the model
     */
    @Override
    default ActorRef<DevsMessage> provideSimulator(ActorContext<DevsMessage> context, T initialTime) {
        return Actors.spawn(context, PDevsSimulator.create(createModel(initialTime), initialTime),
                ModelUtils.toLegalActorName(getModelIdentifier()));
    }
}
