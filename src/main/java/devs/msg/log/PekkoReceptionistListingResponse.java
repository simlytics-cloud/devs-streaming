package devs.msg.log;

import org.apache.pekko.actor.typed.receptionist.Receptionist;
import devs.msg.DevsMessage;

public class PekkoReceptionistListingResponse implements DevsMessage {
    final Receptionist.Listing listing;

    public PekkoReceptionistListingResponse(Receptionist.Listing listing) {
      this.listing = listing;
    }

    public Receptionist.Listing getListing() {
      return listing;
    }


}
