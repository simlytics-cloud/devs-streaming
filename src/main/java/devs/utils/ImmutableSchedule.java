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

package devs.utils;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import devs.iso.time.SimTime;
import devs.iso.time.SimTimeKeyDeserializer;
import devs.iso.time.SimTimeKeySerializer;
import devs.msg.mutability.Immutable;
import devs.msg.mutability.MutabilityUtil;
import devs.utils.Schedule.Event;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@EqualsAndHashCode
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = lombok.AccessLevel.PUBLIC, force = true)
public class ImmutableSchedule<T extends SimTime> implements Immutable {

  @JsonSerialize(keyUsing = SimTimeKeySerializer.class)
  @JsonDeserialize(keyUsing = SimTimeKeyDeserializer.class)
  protected com.google.common.collect.ImmutableSortedMap<T, List<Event>> schedule
      = com.google.common.collect.ImmutableSortedMap.of();
  
  
  public ImmutableSchedule(com.google.common.collect.ImmutableSortedMap<T, List<Event>> schedule) {
    this.schedule = schedule;
  }

  public ImmutableSchedule(TreeMap<T, ArrayList<Event>> schedule) {
    this.schedule = MutabilityUtil.toImmutableMap(schedule);
  }
  
  @Override
  public Schedule<T> toMutable() {
    return new Schedule<>(MutabilityUtil.toMutableMap(schedule));
  }
  
}
