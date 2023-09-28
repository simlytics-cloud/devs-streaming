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

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Interface used to mark messages to be logged by a DevsLoggingActor.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    property = "devsType"
)
public interface DevsLogMessage {

}
