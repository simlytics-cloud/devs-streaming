package devs.msg;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Marker interface to allow receiving external messages.  This interface is typically used to 
 * mark responses from requests sent to external actors or API's by a PDEVSModel during
 * a state transition.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public abstract interface DevsExternalMessage extends DevsMessage {

}

