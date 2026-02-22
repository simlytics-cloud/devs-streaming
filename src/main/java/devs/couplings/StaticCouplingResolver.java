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
 * Resolves to a fixed list of targets regardless of message content.
 */
public class StaticCouplingResolver extends CouplingResolver {
    private final List<CouplingTarget> targets;

    public StaticCouplingResolver(List<CouplingTarget> targets) {
        this.targets = List.copyOf(targets);
    }

    @Override
    public List<CouplingTarget> resolve(String sender, PortValue<?> portValue) {
        return targets;
    }

    public List<CouplingTarget> getTargets() {
        return targets;
    }
}
