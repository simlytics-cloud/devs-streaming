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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import devs.msg.time.SimTime;
import org.immutables.value.Value;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Value.Immutable
@JsonSerialize(as = OutputReport.class)
@JsonDeserialize(as = OutputReport.class)
public abstract class AbstractOutputReport<T extends SimTime> extends DevsSimMessage implements
    TimedMessage<T>, HasPayload<OutputReportPayload> {

  @Override
  public SimMessageType getMessageType() {
    return SimMessageType.OutputReport;
  }

  @JsonDeserialize(using = FlexibleNumberDeserializer.class)
  @JsonSerialize(using = FlexibleNumberSerializer.class)
  @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
  public abstract T getNextInternalTime();
}
