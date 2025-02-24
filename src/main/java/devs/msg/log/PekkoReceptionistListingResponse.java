package devs.msg.log;

import devs.msg.DevsMessage;
import org.apache.pekko.actor.typed.receptionist.Receptionist;

/**
 * Returns the Apache Pekko Receptionist listing identifying models that have registered
 * with the receptionist.
 */
public class PekkoReceptionistListingResponse implements DevsMessage {
  final Receptionist.Listing listing;

  public PekkoReceptionistListingResponse(Receptionist.Listing listing) {
    this.listing = listing;
  }

  public Receptionist.Listing getListing() {
    return listing;
  }


}
