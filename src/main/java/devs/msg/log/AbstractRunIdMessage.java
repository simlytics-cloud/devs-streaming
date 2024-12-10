package devs.msg.log;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;


@Value.Immutable
@JsonSerialize(as = RunIdMessage.class)
@JsonDeserialize(as = RunIdMessage.class)
public abstract class AbstractRunIdMessage {

  public abstract String getRunId();

  public abstract DevsLogMessage getDevsLogMessage();

}
