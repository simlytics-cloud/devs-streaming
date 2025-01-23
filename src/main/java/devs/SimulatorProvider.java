package devs;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import devs.msg.DevsMessage;
import devs.msg.time.SimTime;

public interface SimulatorProvider<T extends SimTime> {

    abstract ActorRef<DevsMessage> provideSimulator(ActorContext<DevsMessage> context, T initialTime);

    public abstract String getModelIdentifier();
    
}
