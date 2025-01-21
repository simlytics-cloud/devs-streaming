/*
 * DEVS Streaming Framework Java Copyright (C) 2024 simlytics.cloud LLC and
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

package devs.msg;

import com.fasterxml.jackson.annotation.JsonTypeInfo;


/**
 * Marker interface for all DEVS framework messages.
 * <p>
 * This interface serves as a base for various message types used within the DEVS Streaming
 * Framework. Implementing this interface allows messages to be categorized and passed between
 * different simulation components and actors.
 * <p>
 * The framework utilizes Jackson's {@link JsonTypeInfo} for polymorphic message serialization and
 * deserialization, associating each message with a specific type identifier.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "devsType")
public interface DevsMessage {

}
