package devs.msg.log;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;


/**
 * Represents an abstract message containing a run identifier and a logging message. This class is
 * immutable and designed to be serialized and deserialized with JSON. It serves as a base class for
 * concrete implementations such as {@code RunIdMessage}.
 */
@Value.Immutable
@JsonSerialize(as = RunIdMessage.class)
@JsonDeserialize(as = RunIdMessage.class)
public abstract class AbstractRunIdMessage {

  /**
   * Retrieves the unique identifier associated with the run.
   *
   * @return A string representing the run identifier.
   */
  public abstract String getRunId();

  /**
   * Retrieves the logging message associated with this object. The returned message is an
   * implementation of {@link DevsLogMessage}, which serves as a base interface for various types of
   * logging messages within the DEVS framework.
   *
   * @return An instance of {@link DevsLogMessage}, representing the logging information for this
   * object. Subtypes may include specific details such as model state or simulation events.
   */
  public abstract DevsLogMessage getDevsLogMessage();

}
