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
 * A resolver that maps messages to a target model, encoding the sender's
 * identity into the target port name for observation purposes.
 */
public class SourceMappingResolver extends CouplingResolver {
    private final String targetModel;
    private final String basePortName;
    private final String separator;

    public SourceMappingResolver(String targetModel, String basePortName) {
        this(targetModel, basePortName, "_");
    }

    public SourceMappingResolver(String targetModel, String basePortName, String separator) {
        this.targetModel = targetModel;
        this.basePortName = basePortName;
        this.separator = separator;
    }

    @Override
    public List<CouplingTarget> resolve(String sender, PortValue<?> portValue) {
        // Construct the dynamic port name: "uav-01_position"
        String mappedPort = sender + separator + basePortName;
        return List.of(CouplingTarget.of(targetModel, mappedPort));
    }
}
