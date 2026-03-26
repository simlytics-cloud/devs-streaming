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
import devs.observation.ObservationUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SourceMappingResolverTest {

    @Test
    public void testSourceMappingResolver() {
        SourceMappingResolver resolver = new SourceMappingResolver("targetModel", "basePort");
        PortValue<String> portValue = PortValue.<String>builder()
                .value("testValue")
                .portName("originalPort")
                .build();

        List<CouplingTarget> targets = resolver.resolve("senderId", portValue);

        assertEquals(1, targets.size());
        assertEquals("targetModel", targets.get(0).targetModel());
        assertEquals("senderId_basePort", targets.get(0).targetPort());
    }

    @Test
    public void testSourceMappingResolverWithCustomSeparator() {
        SourceMappingResolver resolver = new SourceMappingResolver("targetModel", "basePort", "-");
        PortValue<String> portValue = PortValue.<String>builder()
                .value("testValue")
                .portName("originalPort")
                .build();

        List<CouplingTarget> targets = resolver.resolve("senderId", portValue);

        assertEquals(1, targets.size());
        assertEquals("targetModel", targets.get(0).targetModel());
        assertEquals("senderId-basePort", targets.get(0).targetPort());
    }

    @Test
    public void testObservationUtils() {
        assertEquals("uav-01", ObservationUtils.extractProducerId("uav-01_position", "default"));
        assertEquals("uav-01", ObservationUtils.extractProducerId("uav-01-position", "-", "default"));
        assertEquals("default", ObservationUtils.extractProducerId("noSeparatorPort", "default"));
        assertEquals("default", ObservationUtils.extractProducerId(null, "default"));
    }
}
