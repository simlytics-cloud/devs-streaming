package devs;

import org.apache.pekko.actor.typed.Behavior;
import devs.msg.log.DevsLogMessage;

public interface DevsLoggerFactory {
    public abstract Behavior<DevsLogMessage> createDevsLogMessageBehaior();
}
