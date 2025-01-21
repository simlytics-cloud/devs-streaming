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

/**
 * Represents a core interface that extends {@link DevsMessage} and provides the capability
 * to retrieve the identifier of the message sender.
 * The sender identifier is typically used for tracing the origin of a message
 * within the DEVS Streaming Framework.
 *
 * Implementations of this interface can be used to represent messages that
 * include sender information, enabling the framework to handle and route messages
 * based on their originating source.
 */
public interface Sender extends DevsMessage {

  /**
   * Retrieves the identifier of the message sender.
   *
   * @return the sender identifier as a String.
   */
  public String getSender();
}
