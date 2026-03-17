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

package devs.iso.log;

import devs.iso.DevsMessage;
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
