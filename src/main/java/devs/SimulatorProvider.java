package devs;

import devs.iso.DevsMessage;
import devs.iso.time.SimTime;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;

/**
 * Interface for a class that provices a DEVS Simulator for a DEVS Model or a Coordinator for a
 * coupled model.
 */
public interface SimulatorProvider<T extends SimTime> {

  /**
   * Returns a DEVS Simulator or DEVS Coordinator for a DEVS model.
   *
   * @param context the actor context
   * @param initialTime the initial time of the simulation
   * @return the DEVS Simulator or Coordinator to execute the model
   */
  abstract ActorRef<DevsMessage> provideSimulator(ActorContext<DevsMessage> context, T initialTime);

  /**
   * Returns the model identifier of the underlying DEVS model.
   *
   * @return the model identifier
   */
  public abstract String getModelIdentifier();

}
