/*
 * DEVS Streaming Framework
 * Copyright (C) 2023  simlytics.cloud LLC and DEVS Streaming Framework contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package devs.msg.log;

import org.immutables.value.Value;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import devs.msg.DevsMessage;
import devs.msg.time.SimTime;
import devs.msg.time.TimedDevsMessage;


@Value.Immutable
@JsonSerialize(as = DevsModelLogMessage.class)
@JsonDeserialize(as = DevsModelLogMessage.class)
public abstract class AbstractDevsModelLogMessage<T extends SimTime> implements DevsLogMessage, TimedDevsMessage<T> {
    @Override
    @Value.Parameter
    public abstract T getTime();

    @Value.Parameter
    public abstract String getModelId();

    @Value.Parameter
    public abstract DevsMessage getDevsMessage();
}