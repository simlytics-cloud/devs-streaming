/*
 * DEVS Streaming Framework Java Copyright (C) 2026 simlytics.cloud LLC and
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

package devs.couplings;

import devs.iso.PortValue;
import java.util.List;

/**
 * Base class for resolving destinations for a given message.
 */
public abstract class CouplingResolver {
    /**
     * Resolves the coupling targets based on the sender and the message content.
     *
     * @param sender The identifier of the sender model.
     * @param portValue The message being routed.
     * @return A list of target destinations.
     */
    public abstract List<CouplingTarget> resolve(String sender, PortValue<?> portValue);
}
