package devs.msg.log;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;


@Value.Immutable
@JsonSerialize(as = RunIdMessage.class)
@JsonDeserialize(as = RunIdMessage.class)
public abstract class AbstractRunIdMessage {

    public abstract String getRunId();
    public abstract DevsLogMessage getDevsLogMessage();
    
}
