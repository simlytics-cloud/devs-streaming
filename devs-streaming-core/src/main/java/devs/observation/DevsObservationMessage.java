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


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import devs.msg.Branch;
import devs.msg.Run;



/**
 * Marker interface for observation messages exchanged between routers and sink actors.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonSubTypes({
    @Type(value = Observation.class),
    @Type(value = Run.class),
    @Type(value = Branch.class),
    @Type(value = StopLogger.class),
    @Type(value = ObservationTypeEntry.class)
})
public interface DevsObservationMessage {

}
