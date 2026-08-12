/*
 * DEVS Streaming Framework Java Copyright (C) 2025 simlytics.cloud LLC and
 * DEVS Streaming Framework Java contributors.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package devs.observation;

import devs.iso.DevsMessage;
import org.apache.pekko.actor.typed.receptionist.Receptionist;

/**
 * Wraps a Pekko receptionist listing so it can be exchanged through the DEVS message layer.
 */
public class PekkoReceptionistListingResponse implements DevsMessage {
  final Receptionist.Listing listing;

  /**
   * Creates a message wrapper for a receptionist listing update.
   *
   * @param listing listing received from the Pekko receptionist
   */
  public PekkoReceptionistListingResponse(Receptionist.Listing listing) {
    this.listing = listing;
  }

  /**
   * Returns the receptionist listing carried by this DEVS message.
   *
   * @return receptionist listing update
   */
  public Receptionist.Listing getListing() {
    return listing;
  }
}
