package devs.msg.log;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


/**
 * Class used to serialize messages by a DevsLoggingActor.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS
)
@JsonSubTypes({
    @Type(value = DevsModelLogMessage.class),
    @Type(value = StateMessage.class)
})
public abstract interface DevsLogMessage {

}
