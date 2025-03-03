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

package devs;

import devs.msg.Bag;
import java.util.Map;


/**
 * The OutputCouplingMessages class represents a structure containing output messages and internal
 * messages for a coupled PDevs model. It is used to encapsulate and transport these message types
 * during the model's execution.
 * <p>
 * This class includes the following attributes: - An output message encapsulated in a Bag object. -
 * Internal messages mapped to their corresponding Bag instances, organized by string keys.
 * <p>
 * This structure provides convenience methods for accessing the output message and the internal
 * messages mapping.
 */
public class OutputCouplingMessages {

  final Bag outputMessage;
  final Map<String, Bag> internalMessages;

  public OutputCouplingMessages(Bag outputMessage, Map<String, Bag> internalMessages) {
    this.outputMessage = outputMessage;
    this.internalMessages = internalMessages;
  }

  public Bag getOutputMessage() {
    return outputMessage;
  }

  public Map<String, Bag> getInternalMessages() {
    return internalMessages;
  }
}
