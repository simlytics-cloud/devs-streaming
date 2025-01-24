package devs;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import devs.msg.DevsMessage;
import devs.msg.time.SimTime;
import devs.utils.ModelUtils;

public class DevsSimulatorProvider<T extends SimTime> extends LoggingSimulatorProvider<T> {
    protected final PDEVSModel<T, ?> devsModel;

    public DevsSimulatorProvider(PDEVSModel<T, ?> devsModel) {
      this.devsModel = devsModel;
    }

    @Override
    public ActorRef<DevsMessage> provideSimulator(ActorContext<DevsMessage> context, T initialTime) {
        if (loggingModels.contains(devsModel.getModelIdentifier())) {
            return context.spawn(StateLoggingSimulator.createStateLoggingSimulator(devsModel, initialTime), 
            ModelUtils.toLegalActorName(getModelIdentifier()));
        } else {
            return context.spawn(PDevsSimulator.create(devsModel, initialTime), 
            ModelUtils.toLegalActorName(devsModel.getModelIdentifier()));
        }
    }

    @Override
    public String getModelIdentifier() {
      return devsModel.getModelIdentifier();
    }
  
}
