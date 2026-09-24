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
 */

package devs.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.config.Config;
import iso.sim.server.dto.run.CoordinatorHelperCallbackConfigurationDto;
import iso.sim.server.dto.run.TimeModeDto;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Input for starting a remote model.
 *
 * @param hostUrl                 remote model server URL
 * @param modelId                 fully qualified remote model identifier
 * @param runId                   caller-supplied simulation run identifier or prefix
 * @param appendGeneratedRunSuffix whether to append coordinator and UUID values to {@code runId}
 * @param modelInstanceId         instance identifier within the simulation
 * @param coordinatorIdentifier   coordinator identifier
 * @param timeMode                simulation time mode
 * @param kafkaTopic              topic used for model communication
 * @param kafkaConfig             shared Kafka client configuration
 * @param topicResetMode          topic lifecycle behavior
 * @param initializationParameters model initialization parameters
 * @param coordinatorHelper       optional coordinator helper callback configuration
 */
public record RemoteModelStartRequest(
    String hostUrl,
    String modelId,
    String runId,
    boolean appendGeneratedRunSuffix,
    String modelInstanceId,
    String coordinatorIdentifier,
    TimeModeDto timeMode,
    String kafkaTopic,
    Config kafkaConfig,
    TopicResetMode topicResetMode,
    JsonNode initializationParameters,
    @Nullable CoordinatorHelperCallbackConfigurationDto coordinatorHelper) {

  public RemoteModelStartRequest {
    Objects.requireNonNull(hostUrl, "hostUrl");
    Objects.requireNonNull(modelId, "modelId");
    Objects.requireNonNull(runId, "runId");
    Objects.requireNonNull(modelInstanceId, "modelInstanceId");
    Objects.requireNonNull(coordinatorIdentifier, "coordinatorIdentifier");
    Objects.requireNonNull(timeMode, "timeMode");
    Objects.requireNonNull(kafkaTopic, "kafkaTopic");
    Objects.requireNonNull(kafkaConfig, "kafkaConfig");
    Objects.requireNonNull(topicResetMode, "topicResetMode");
    Objects.requireNonNull(initializationParameters, "initializationParameters");
  }
}
