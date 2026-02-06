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

package devs.msg.state;

import devs.iso.time.SimTime;
import devs.utils.ImmutableSchedule;
import devs.utils.Schedule;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = lombok.AccessLevel.PUBLIC, force = true)
public class ImmutableScheduleState<T extends SimTime> extends ImmutableTimeState<T> implements IScheduleState<T> {
  @NonNull
  ImmutableSchedule<T> schedule;

  public ImmutableScheduleState(T currentTime, Schedule<T> schedule) {
    super(currentTime);
    this.schedule = schedule.toImmutable();
  }

  public ImmutableScheduleState(T currentTime, ImmutableSchedule<T> schedule) {
    super(currentTime);
    this.schedule = schedule;
  }


  public ImmutableScheduleState(T currentTime) {
    super(currentTime);
    this.schedule = new ImmutableSchedule<>();
  }

  public ImmutableSchedule<T> getSchedule() {
    return schedule;
  }
  
}
