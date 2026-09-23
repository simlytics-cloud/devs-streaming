package devs.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import devs.SimulatorProvider;
import devs.iso.time.SimTime;
import devs.proxy.KafkaLocalProxy;
import iso.sim.server.dto.run.*;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.model.*;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class RemoteModelStarter {

    ObjectMapper objectMapper = new ObjectMapper();

    public <T extends SimTime> SimulatorProvider<T> startRemoteModelTopicExists(String hostUrl,
                                                                                  String fullyQualifiedModelId,
                                                                                  String runId,
                                                                                  String modelInstanceId,
                                                                                  String coordinatorIdentifier,
                                                                                  TimeModeDto timeMode,
                                                                                  String kafkaTopic,
                                                                                  Config kafkaClusterConfig,
                                                                                  Config kafkaConsumerConfig,
                                                                                  JsonNode initializationParameters) {
        return startRemoteModelTopicExists(hostUrl, fullyQualifiedModelId, runId, modelInstanceId,
                coordinatorIdentifier, timeMode, kafkaTopic, kafkaClusterConfig, kafkaConsumerConfig,
                initializationParameters, null);
    }

    public <T extends SimTime> SimulatorProvider<T> startRemoteModelTopicExists(String hostUrl, String fullyQualifiedModelId, String runId,
                                                                     String modelInstanceId, String coordinatorIdentifier,
                                                                     TimeModeDto timeMode, String kafkaTopic,
                                                                     Config kafkaClusterConfig, Config kafkaConsumerConfig,
                                                                     JsonNode initializationParameters, @Nullable CoordinatorHelperCallbackConfigurationDto coordinatorHelper) {
        SimulationContextDto simulationContextDto = new SimulationContextDto(
                runId, modelInstanceId, coordinatorIdentifier, timeMode);
        KafkaConfigurationDto kafkaConfigurationDto = new KafkaConfigurationDto("localhost:29092",
                kafkaTopic, KafkaSecurityProtocol.PLAINTEXT, KafkaSaslMechanism.PLAIN,
                new HashMap<>());
        StartModelRunRequest startModelRunRequest = new StartModelRunRequest(runId,
                initializationParameters, kafkaConfigurationDto, simulationContextDto, coordinatorHelper);
        String actorName = ModelUtils.toLegalActorSystemName(coordinatorIdentifier) + "-test-http";
        ActorSystem actorSystem = ActorSystem.create(actorName);
        Http http = Http.get(actorSystem);

        String startModelRunRequestJson;
        try {
            startModelRunRequestJson = objectMapper.writeValueAsString(startModelRunRequest);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize startModelRunRequest", e);
        }
        String url = hostUrl + "/v1/models/" + fullyQualifiedModelId + "/run";
        HttpRequest startModelRunHttpRequest = HttpRequest.create()
                .withMethod(HttpMethods.PUT)
                .withUri(url)
                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, startModelRunRequestJson));
        try {
            HttpResponse startModelRunResponse = http.singleRequest(startModelRunHttpRequest)
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
            String startModelRunResponseBody = startModelRunResponse.entity()
                    .toStrict(30_000, actorSystem)
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS)
                    .getData()
                    .utf8String();

            if (!startModelRunResponse.status().equals(StatusCodes.ACCEPTED)) {
                throw new RuntimeException("startModelRun request failed with status "
                        + startModelRunResponse.status() + ": " + startModelRunResponseBody);
            }

            JsonNode responseJson = objectMapper.readTree(startModelRunResponseBody);
            if (!responseJson.hasNonNull("runId")
                    || !responseJson.get("runId").isTextual()
                    || !responseJson.hasNonNull("modelId")
                    || !responseJson.get("modelId").isTextual()
                    || !responseJson.hasNonNull("status")
                    || !responseJson.get("status").isTextual()
                    || !"accepted".equalsIgnoreCase(responseJson.get("status").asText())
                    || !responseJson.hasNonNull("statusUrl")
                    || !responseJson.get("statusUrl").isTextual()
                    || !responseJson.hasNonNull("acceptedAt")
                    || !responseJson.get("acceptedAt").isTextual()
                    || !responseJson.hasNonNull("message")
                    || !responseJson.get("message").isTextual()) {
                throw new RuntimeException("Invalid startModelRun response body: " + startModelRunResponseBody);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to start remote model run", e);
        }

        KafkaLocalProxy.ProxyProperties bifrostProxyProperties = new KafkaLocalProxy.ProxyProperties(
                runId,
                coordinatorIdentifier,
                kafkaTopic,
                kafkaClusterConfig,
                modelInstanceId,
                kafkaTopic,
                kafkaConsumerConfig
        );
        KafkaLocalProxy.KafkaProxySimulatorProvider<T> kafkaSimulatorProvider =
                new KafkaLocalProxy.KafkaProxySimulatorProvider<>(bifrostProxyProperties);

        return kafkaSimulatorProvider;

    }

    public <T extends SimTime> SimulatorProvider<T> startRemoteModel(String hostUrl,
                                                                      String fullyQualifiedModelId,
                                                                      String simulationId,
                                                                      String modelInstanceId,
                                                                      String coordinatorIdentifier,
                                                                      TimeModeDto timeMode,
                                                                      String kafkaTopic,
                                                                      Config kafkaClusterConfig,
                                                                      Config kafkaConsumerConfig,
                                                                      JsonNode initializationParameters) {
        return startRemoteModel(hostUrl, fullyQualifiedModelId, simulationId, modelInstanceId,
                coordinatorIdentifier, timeMode, kafkaTopic, kafkaClusterConfig, kafkaConsumerConfig,
                initializationParameters, null);
    }

    public <T extends SimTime> SimulatorProvider<T> startRemoteModel(String hostUrl, String fullyQualifiedModelId, String simulationId,
                                                                     String modelInstanceId, String coordinatorIdentifier,
                                                                     TimeModeDto timeMode, String kafkaTopic,
                                                                     Config kafkaClusterConfig, Config kafkaConsumerConfig,
                                                                     JsonNode initializationParameters,
                                                                     @Nullable CoordinatorHelperCallbackConfigurationDto coordinatorHelper) {

        Properties adminProperties = new Properties();
        simulationId = simulationId + "-" + coordinatorIdentifier + "-" + java.util.UUID.randomUUID();
        adminProperties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaClusterConfig.getString("bootstrap.servers"));
        try (AdminClient adminClient = AdminClient.create(adminProperties)) {
            try {
                adminClient.deleteTopics(List.of(kafkaTopic)).all().get(30, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // Ignore delete failures (e.g., topic does not exist) and proceed to create.
            }
            adminClient.createTopics(List.of(new NewTopic(kafkaTopic, 1, (short) 1)))
                    .all()
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to recreate kafka topic " + kafkaTopic, e);
        }
        SimulationContextDto simulationContextDto = new SimulationContextDto(
                simulationId, modelInstanceId, coordinatorIdentifier, timeMode);
        KafkaConfigurationDto kafkaConfigurationDto = new KafkaConfigurationDto("localhost:29092",
                kafkaTopic, KafkaSecurityProtocol.PLAINTEXT, KafkaSaslMechanism.PLAIN,
                new HashMap<>());
        StartModelRunRequest startModelRunRequest = new StartModelRunRequest(simulationId,
                initializationParameters, kafkaConfigurationDto, simulationContextDto, coordinatorHelper);
        String actorName = ModelUtils.toLegalActorSystemName(coordinatorIdentifier) + "-test-http";
        ActorSystem actorSystem = ActorSystem.create(actorName);
        Http http = Http.get(actorSystem);

        String startModelRunRequestJson;
        try {
            startModelRunRequestJson = objectMapper.writeValueAsString(startModelRunRequest);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize startModelRunRequest", e);
        }
        String url = hostUrl + "/v1/models/" + fullyQualifiedModelId + "/run";
        HttpRequest startModelRunHttpRequest = HttpRequest.create()
                .withMethod(HttpMethods.PUT)
                .withUri(url)
                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, startModelRunRequestJson));
        try {
            HttpResponse startModelRunResponse = http.singleRequest(startModelRunHttpRequest)
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
            String startModelRunResponseBody = startModelRunResponse.entity()
                    .toStrict(30_000, actorSystem)
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS)
                    .getData()
                    .utf8String();

            if (!startModelRunResponse.status().equals(StatusCodes.ACCEPTED)) {
                throw new RuntimeException("startModelRun request failed with status "
                        + startModelRunResponse.status() + ": " + startModelRunResponseBody);
            }

            JsonNode responseJson = objectMapper.readTree(startModelRunResponseBody);
            if (!responseJson.hasNonNull("runId")
                    || !responseJson.get("runId").isTextual()
                    || !responseJson.hasNonNull("modelId")
                    || !responseJson.get("modelId").isTextual()
                    || !responseJson.hasNonNull("status")
                    || !responseJson.get("status").isTextual()
                    || !"accepted".equalsIgnoreCase(responseJson.get("status").asText())
                    || !responseJson.hasNonNull("statusUrl")
                    || !responseJson.get("statusUrl").isTextual()
                    || !responseJson.hasNonNull("acceptedAt")
                    || !responseJson.get("acceptedAt").isTextual()
                    || !responseJson.hasNonNull("message")
                    || !responseJson.get("message").isTextual()) {
                throw new RuntimeException("Invalid startModelRun response body: " + startModelRunResponseBody);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to start remote model run", e);
        }

        KafkaLocalProxy.ProxyProperties bifrostProxyProperties = new KafkaLocalProxy.ProxyProperties(
                simulationId,
                coordinatorIdentifier,
                kafkaTopic,
                kafkaClusterConfig,
                modelInstanceId,
                kafkaTopic,
                kafkaConsumerConfig
        );
        KafkaLocalProxy.KafkaProxySimulatorProvider<T> kafkaSimulatorProvider =
                new KafkaLocalProxy.KafkaProxySimulatorProvider<>(bifrostProxyProperties);

        return kafkaSimulatorProvider;
    }
}
