package devs.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import devs.SimulatorProvider;
import devs.iso.time.SimTime;
import devs.proxy.KafkaLocalProxy;
import iso.sim.server.dto.run.KafkaConfigurationDto;
import iso.sim.server.dto.run.SimulationContextDto;
import iso.sim.server.dto.run.StartModelRunRequest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.model.HttpMethods;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.StatusCodes;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Starts remote models and creates the corresponding local Kafka proxy provider.
 */
public class RemoteModelStarter {

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Starts a remote model using the supplied request.
   *
   * @param request remote model start parameters
   * @param <T> simulation time type
   * @return a provider for the local Kafka proxy simulator
   */
  public <T extends SimTime> SimulatorProvider<T> startRemoteModel(
      RemoteModelStartRequest request) {
    String runId = request.appendGeneratedRunSuffix()
        ? request.runId() + "-" + request.coordinatorIdentifier() + "-" + UUID.randomUUID()
        : request.runId();

    prepareKafkaTopic(request);
    startRemoteModelRun(request, runId);

    KafkaLocalProxy.ProxyProperties proxyProperties = new KafkaLocalProxy.ProxyProperties(
        runId,
        request.coordinatorIdentifier(),
        request.kafkaTopic(),
        request.modelInstanceId(),
        request.kafkaTopic(),
        request.kafkaConfig());
    return new KafkaLocalProxy.KafkaProxySimulatorProvider<>(proxyProperties);
  }

  private void prepareKafkaTopic(RemoteModelStartRequest request) {
    try (AdminClient adminClient = KafkaUtils.createAdminClient(request.kafkaConfig())) {
      KafkaUtils.ensureTopic(request.kafkaTopic(), adminClient, request.topicResetMode());
    } catch (Exception e) {
      throw new RuntimeException("Failed to prepare Kafka topic " + request.kafkaTopic(), e);
    }
  }

  private void startRemoteModelRun(RemoteModelStartRequest request, String runId) {
    SimulationContextDto simulationContextDto = new SimulationContextDto(
        runId, request.modelInstanceId(), request.coordinatorIdentifier(), request.timeMode());
    KafkaConfigurationDto kafkaConfigurationDto = new KafkaConfigurationDto(request.kafkaTopic(),
        KafkaUtils.toStringProperties(request.kafkaConfig()));
    StartModelRunRequest startModelRunRequest = new StartModelRunRequest(runId,
        request.initializationParameters(), kafkaConfigurationDto, simulationContextDto,
        request.coordinatorHelper());

    ActorSystem actorSystem = ActorSystem.create(
        ModelUtils.toLegalActorSystemName(request.coordinatorIdentifier()) + "-test-http");
    Http http = Http.get(actorSystem);
    String startModelRunRequestJson = serializeRequest(startModelRunRequest);
    HttpRequest startModelRunHttpRequest = HttpRequest.create()
        .withMethod(HttpMethods.PUT)
        .withUri(request.hostUrl() + "/v1/models/" + request.modelId() + "/run")
        .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, startModelRunRequestJson));

    try {
      HttpResponse startModelRunResponse = http.singleRequest(startModelRunHttpRequest)
          .toCompletableFuture()
          .get(30, TimeUnit.SECONDS);
      String responseBody = startModelRunResponse.entity()
          .toStrict(30_000, actorSystem)
          .toCompletableFuture()
          .get(30, TimeUnit.SECONDS)
          .getData()
          .utf8String();
      validateResponse(startModelRunResponse, responseBody);
    } catch (Exception e) {
      throw new RuntimeException("Failed to start remote model run", e);
    }
  }

  private String serializeRequest(StartModelRunRequest startModelRunRequest) {
    try {
      return objectMapper.writeValueAsString(startModelRunRequest);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize startModelRunRequest", e);
    }
  }

  private void validateResponse(HttpResponse response, String responseBody)
      throws JsonProcessingException {
    if (!response.status().equals(StatusCodes.ACCEPTED)) {
      throw new RuntimeException(
          "startModelRun request failed with status " + response.status() + ": " + responseBody);
    }

    JsonNode responseJson = objectMapper.readTree(responseBody);
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
      throw new RuntimeException("Invalid startModelRun response body: " + responseBody);
    }
  }
}
