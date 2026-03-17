package devs;

import devs.iso.DevsMessage;
import devs.iso.time.SimTime;
import devs.utils.ModelUtils;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;

/**
 * Provides a DEVS Simulator for a DEVS Model.
 */
public class DevsSimulatorProvider<T extends SimTime> implements SimulatorProvider<T> {
  protected final PDEVSModel<T, ?> devsModel;

  public DevsSimulatorProvider(PDEVSModel<T, ?> devsModel) {
    this.devsModel = devsModel;
  }

  /**
   * Provides the DEVS Simulator for the underlying PDEVSModel.
   */
  @Override
  public ActorRef<DevsMessage> provideSimulator(ActorContext<DevsMessage> context, T initialTime) {
    return context.spawn(PDevsSimulator.create(devsModel, initialTime),
        ModelUtils.toLegalActorName(devsModel.getModelIdentifier()));
  }

  @Override
  public String getModelIdentifier() {
    return devsModel.getModelIdentifier();
  }

}
