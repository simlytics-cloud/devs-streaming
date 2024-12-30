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

package devs.msg.log;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;


/**
 * Represents an abstract message containing a run identifier and a logging message. This class is
 * immutable and designed to be serialized and deserialized with JSON. It serves as a base class for
 * concrete implementations such as {@code RunIdMessage}.
 */
@Value.Immutable
@JsonSerialize(as = RunIdMessage.class)
@JsonDeserialize(as = RunIdMessage.class)
public abstract class AbstractRunIdMessage {

  /**
   * Retrieves the unique identifier associated with the run.
   *
   * @return A string representing the run identifier.
   */
  public abstract String getRunId();

  /**
   * Retrieves the logging message associated with this object. The returned message is an
   * implementation of {@link DevsLogMessage}, which serves as a base interface for various types of
   * logging messages within the DEVS framework.
   *
   * @return An instance of {@link DevsLogMessage}, representing the logging information for this
   * object. Subtypes may include specific details such as model state or simulation events.
   */
  public abstract DevsLogMessage getDevsLogMessage();

}
