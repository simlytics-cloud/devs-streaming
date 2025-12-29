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

package devs.iso;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import devs.utils.DevsTypeIdResolver;
import org.immutables.value.Value;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.CUSTOM,
    include = JsonTypeInfo.As.PROPERTY,
    property = "messageType"
)
@JsonTypeIdResolver(DevsTypeIdResolver.class)
@JsonSubTypes({
    @Type(value = SimulationInit.class),
    @Type(value = SimulationTerminate.class),
    @Type(value = NextInternalTimeReport.class),
    @Type(value = RequestOutput.class),
    @Type(value = OutputReport.class),
    @Type(value = ExecuteTransition.class)
})
public abstract class DevsSimMessage implements DevsMessage {
  public abstract String getSimulationId();
  public abstract String getMessageId();
  @Value.Derived
  public abstract SimMessageType getMessageType();
  public abstract String getSenderId();
  @Nullable
  public abstract String getCorrelationId();
}