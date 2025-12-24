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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


import devs.iso.DevsExternalMessage;
import devs.iso.DevsMessage;
import devs.iso.ExecuteTransition;
import devs.iso.ExecuteTransitionPayload;
import devs.iso.ModelIdPayload;
import devs.iso.NextInternalTimeReport;
import devs.iso.OutputReport;
import devs.iso.PortValue;
import devs.iso.RequestOutput;
import devs.iso.SimulationInit;
import devs.iso.SimulationInitMessage;

import devs.iso.TransitionComplete;
import devs.iso.time.LongSimTime;
import devs.utils.HttpServiceActor;
import devs.utils.HttpServiceActor.Command;
import devs.utils.HttpServiceActor.ErrorResponse;
import devs.utils.HttpServiceActor.Request;
import devs.utils.HttpServiceActor.SucceffulResponse;
import example.generator.GeneratorModel;
import java.util.List;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Directives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.SystemMaterializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import scala.concurrent.duration.FiniteDuration;

/**
 * Test class for validating the functionality of the PDEVS Simulator.
 * <p>
 * This test ensures the correct behavior of the simulator in terms of initialization, state
 * transitions, output generation, and time advancements within the framework of the Parallel
 * Discrete Event System Specification (PDEVS).
 * <p>
 * It utilizes the Akka TestKit for actor-based testing and verifies the communication and responses
 * of the simulator via defined DEVS framework message interfaces.
 * <p>
 * The test focuses on the following aspects: - Initialization of the simulator with initial time
 * and model state. - Sending input messages and verifying the corresponding outputs. - Executing
 * state transitions and validating changes in system behavior and time. - Ensuring the correct
 * processing of both transition and output generation steps.
 * <p>
 * The class includes a cleanup method to shut down the Akka TestKit after the tests, ensuring
 * proper resource management.
 */
public class ExternalServiceTest {

  static class PekkoHttpServer {

    public static Behavior<Void> create() {
      return Behaviors.setup(context -> {
        var system = context.getSystem();
        Route server = endpoint();
        Http.get(system.classicSystem()).newServerAt("localhost", 8080).bind(server);
        return Behaviors.empty();
      });
    }

    private static Route endpoint() {
      return Directives.concat(
          Directives.path("flip", () ->
              Directives.post(() ->

                  Directives.extractStrictEntity(FiniteDuration.create(1, "second"), entity -> {
                    String inputString = entity.getData().utf8String().trim();
                    int input;
                    try {
                      input = Integer.parseInt(inputString);
                    } catch (NumberFormatException e) {
                      return Directives.complete(StatusCodes.BAD_REQUEST,
                          "Invalid input. Only 0 or 1 are supported.");
                    }
                    int flippedValue = input == 0 ? 1 : (input == 1 ? 0 : -1);
                    if (flippedValue == -1) {
                      return Directives.complete(StatusCodes.BAD_REQUEST,
                          "Invalid input. Only 0 or 1 are supported.");
                    } else {
                      return Directives.complete(StatusCodes.OK, String.valueOf(flippedValue));
                    }
                  })
              )
          )
      );
    }
  }


  public static class PekkoHttpServiceActor extends HttpServiceActor<String, Integer> {

    private final String baseUrl;

    public PekkoHttpServiceActor(ActorContext<Command> context, String baseUrl) {
      super(context, baseUrl);
      this.baseUrl = baseUrl;
    }

    @Override
    public Behavior<Command> onRequest(Request<String> request) {
      // Construct the full HTTP request to be sent to PekkoHttpServer
      HttpRequest httpRequest = HttpRequest.POST(baseUrl + "/flip")
          .withEntity(request.requestData());

      // Enqueue the HTTP request and its associated actor request
      sourceQueue.offer(Pair.create(httpRequest, request));
      return this;
    }

    @Override
    public Behavior<Command> onSingleHttpResponse(SingleHttpResponse<String> singleHttpResponse) {
      // Handle HTTP response, including error status
      HttpResponse response = singleHttpResponse.response();
      Request<String> originalRequest = singleHttpResponse.request();

      if (response.status().isSuccess()) {
        response.entity().toStrict(1000L, testKit.system())
            .thenApply(strictEntity -> strictEntity.getData().utf8String())
            .thenAccept(responseBody -> {
              try {
                Integer result = Integer.parseInt(responseBody); // Parse response to integer
                originalRequest.requester()
                    .tell(new SucceffulResponse<>(result, originalRequest.requestId()));
              } catch (NumberFormatException e) {
                originalRequest.requester().tell(
                    new ErrorResponse("Invalid response format", originalRequest.requestId()));
              }
            });
      } else {
        response.entity().toStrict(1000L, testKit.system())
            .thenApply(strictEntity -> strictEntity.getData().utf8String())
            .thenAccept(responseBody -> originalRequest.requester().tell(
                new ErrorResponse("Error: HTTP " + response.status() + " - " + responseBody,
                    originalRequest.requestId())));
      }

      return this;
    }


    @Override
    public Behavior<Command> onSingleHttpFailure(SingleHttpFailure<String> singleHttpFailure) {
      // Notify requester of failure
      singleHttpFailure.request().requester().tell(
          new ErrorResponse(singleHttpFailure.error(), singleHttpFailure.request().requestId())
      );
      return this;
    }
  }


  static class GeneratorServiceRequest {
    private final int value;
    private final ActorRef<DevsMessage> generatorSimulatorRef;

    public GeneratorServiceRequest(int value, ActorRef<DevsMessage> generatorSimulatorRef) {
      this.value = value;
      this.generatorSimulatorRef = generatorSimulatorRef;
    }

    public int getValue() {
      return value;
    }

    public ActorRef<DevsMessage> getGeneratorSimulatorRef() {
      return generatorSimulatorRef;
    }
  }

  static class GeneratorServiceResponse implements DevsExternalMessage {
    private final int value;

    public GeneratorServiceResponse(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
    
  }

  static class GeneratorService extends AbstractBehavior<GeneratorServiceRequest> {

    public GeneratorService(ActorContext<GeneratorServiceRequest> context) {
      super(context);
    }

    @Override
    public Receive<GeneratorServiceRequest> createReceive() {
      ReceiveBuilder<GeneratorServiceRequest> builder = newReceiveBuilder();
      builder.onMessage(GeneratorServiceRequest.class, this::onRequest);
      return builder.build();
    }

    Behavior<GeneratorServiceRequest> onRequest(GeneratorServiceRequest request) throws InterruptedException {
      System.out.println("Calculating for one second...");
      Thread.sleep(1000);
      Integer modelState = request.getValue();
      if (modelState == 0) {
        modelState = 1;
      } else {
        modelState = 0;
      }
      request.getGeneratorSimulatorRef().tell(new GeneratorServiceResponse(modelState));
      return Behaviors.same();
    }
  }

  static class CalculatingGeneratorModel extends GeneratorModel {
    private final ActorRef<GeneratorServiceRequest> generatorService;

    public CalculatingGeneratorModel(Integer initialState, ActorRef<GeneratorServiceRequest> generatorService) {
      super(initialState);
      this.generatorService = generatorService;
    }

    

    @Override
    public void internalStateTransitionFunction(LongSimTime currentTime) {
      transitionDone = false;
      generatorService.tell(new GeneratorServiceRequest(modelState, simulator.getActorRef()));
    }



    @Override
    protected void processExternalMessage(DevsExternalMessage externalMessage) {
      if (externalMessage instanceof GeneratorServiceResponse generatorServiceResponse) {
        System.out.println("Received calculation.  Transition is now done");
        this.modelState = generatorServiceResponse.getValue();
        transitionDone = true;
      } else {
        throw new IllegalArgumentException("Received unexpected external message of type " 
          + externalMessage.getClass().getCanonicalName());
      }

    }
  }

  static class HttpCalculatingGeneratorModel extends GeneratorModel {
    private final ActorRef<HttpServiceActor.Command> generatorService;

    public HttpCalculatingGeneratorModel(Integer initialState, ActorRef<HttpServiceActor.Command> generatorService) {
      super(initialState);
      this.generatorService = generatorService;
    }



    @Override
    public void internalStateTransitionFunction(LongSimTime currentTime) {
      transitionDone = false;
      generatorService.tell(new Request<String>(String.valueOf(modelState), simulator.getActorRef(), "flip"));
    }



    @Override
    protected void processExternalMessage(DevsExternalMessage externalMessage) {
      if (externalMessage instanceof SucceffulResponse<?> succeffulResponse) {
        if (succeffulResponse.responseData() instanceof Integer responseValue) {
          System.out.println("Received calculation.  Transition is now done");
          this.modelState = responseValue;
          transitionDone = true;
        } else {
          throw new IllegalArgumentException("Service response did not contain an integer value. ");
        }
      } else if (externalMessage instanceof  ErrorResponse errorResponse){
        throw new RuntimeException("Error response received from service: " + errorResponse.error());
      } else {
          throw new IllegalArgumentException("Received unexpected external message of type "
              + externalMessage.getClass().getCanonicalName());
      }

    }
  }
  
  

  /**
   * A static and final variable representing an instance of {@code ActorTestKit}. This test kit is
   * used for testing actor-based implementations in a simulated environment. It provides support
   * for creating test actors and probes, and managing the lifecycle of actors during testing.
   * <p>
   * This instance is initialized once for use across various test cases in the class. It enables
   * consistent testing by simulating actor interactions and capturing messages.
   * <p>
   * The test environment is shut down during cleanup to release resources.
   */
  static final ActorTestKit testKit = ActorTestKit.create();
  static final String simulationId = "external-service-test";

  /**
   * Cleans up resources after all tests in the test class have been executed.
   * <br>
   * This method shuts down the ActorTestKit used in the test class, ensuring proper resource
   * management and cleanup of actor-related resources.
   */
  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }
  
  @Test
  @DisplayName("Test capability for PDEVSSimulator and PDEVSModel to support requests to external" 
      + "actors during state transition")
  void externalActorTest() {
    TestProbe<DevsMessage> probe = testKit.createTestProbe();
    ActorRef<GeneratorServiceRequest> generatorService = testKit.spawn(Behaviors.setup(context ->
        new GeneratorService(context)));
    GeneratorModel generatorModel = new CalculatingGeneratorModel(0,generatorService);
    ActorRef<DevsMessage> simulator = testKit.spawn(Behaviors.setup(
        context -> new PDevsSimulator<LongSimTime, Integer, GeneratorModel>(generatorModel,
            LongSimTime.builder().t(0L).build(), context)));

    // Initialize and expect next sim time to be 1
    simulator.tell(new SimulationInitMessage(SimulationInit.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0))
        .payload(ModelIdPayload.builder().modelId(GeneratorModel.identifier).build())
        .simulationId(simulationId)
        .messageId("SimulationInit")
        .senderId("TestActor")
        .build(), probe.getRef()));
    DevsMessage receivedMessage = probe.receiveMessage();
    assert (receivedMessage instanceof NextInternalTimeReport<?>);
    NextInternalTimeReport nextTime = (NextInternalTimeReport) receivedMessage;
    assert (nextTime.getNextInternalTime() instanceof LongSimTime);
    assert (((LongSimTime) nextTime.getNextInternalTime()).getT() == 1L);
    assert ("generator".equals(nextTime.getSenderId()));

    // Get output and expect it to be 0
    simulator.tell(RequestOutput.<LongSimTime>builder()
        .eventTime(LongSimTime.create(1))
        .payload(ModelIdPayload.builder().modelId(GeneratorModel.identifier).build())
        .simulationId(simulationId)
        .messageId("RequestOutput")
        .senderId("TestActor")
        .build());
    DevsMessage message2 = probe.receiveMessage();
    assert (message2 instanceof OutputReport<?>);
    OutputReport<LongSimTime> modelOutputMessage = (OutputReport<LongSimTime>) message2;
    assert (modelOutputMessage.getNextInternalTime().getT() == 1L);
    List<PortValue<?>> generatorOutput = modelOutputMessage.getPayload().getOutputs();
    assert ((Integer) generatorOutput.get(0).getValue() == 0);

    // Execute transition and expect next time to be 1
    simulator.tell(ExecuteTransition.<LongSimTime>builder()
        .eventTime(LongSimTime.create(1))
        .payload(ExecuteTransitionPayload.builder().modelId(GeneratorModel.identifier).build())
        .simulationId(simulationId)
        .messageId("ExecuteTransition")
        .senderId("TestActor")
        .build());
    DevsMessage message3 = probe.receiveMessage();
    assert (message3 instanceof TransitionComplete<?>);
    TransitionComplete<LongSimTime> transitionDone = (TransitionComplete<LongSimTime>) message3;
    assert (transitionDone.getEventTime().getT() == 1L);

    // Get output and expect it to be 1
    simulator.tell(RequestOutput.<LongSimTime>builder()
        .eventTime(LongSimTime.create(1))
        .payload(ModelIdPayload.builder().modelId(GeneratorModel.identifier).build())
        .simulationId(simulationId)
        .messageId("RequestOutput")
        .senderId("TestActor")
        .build());
    DevsMessage message4 = probe.receiveMessage();
    assert (message4 instanceof OutputReport<?>);
    OutputReport<LongSimTime> modelOutputMessage4 =
        (OutputReport<LongSimTime>) message4;
    List<PortValue<?>> generatorOutput4 = modelOutputMessage4.getPayload().getOutputs();
    assert ((Integer) generatorOutput4.get(0).getValue() == 1);

    // Execute transition and expect next time to be 2
    simulator.tell(ExecuteTransition.<LongSimTime>builder()
        .eventTime(LongSimTime.create(1))
        .payload(ExecuteTransitionPayload.builder().modelId(GeneratorModel.identifier).build())
        .simulationId(simulationId)
        .messageId("ExecuteTransition")
        .senderId("TestActor")
        .build());
    DevsMessage message5 = probe.receiveMessage();
    assert (message5 instanceof TransitionComplete<?>);
    TransitionComplete<LongSimTime> transitionDone2 = (TransitionComplete<LongSimTime>) message5;
    assert (transitionDone2.getNextInternalTime().getT() == 2L);
    assert (transitionDone2.getEventTime().getT() == 1L);

    // Get output and expect it to be 0
    simulator.tell(RequestOutput.<LongSimTime>builder()
        .eventTime(LongSimTime.create(2))
        .payload(ModelIdPayload.builder().modelId(GeneratorModel.identifier).build())
        .simulationId(simulationId)
        .messageId("RequestOutput")
        .senderId("TestActor")
        .build());
    DevsMessage message6 = probe.receiveMessage();
    assert (message6 instanceof OutputReport<?>);
    OutputReport<LongSimTime> modelOutputMessage6 =
        (OutputReport<LongSimTime>) message6;
    List<PortValue<?>> generatorOutput6 = modelOutputMessage6.getPayload().getOutputs();
    assert ((Integer) generatorOutput6.get(0).getValue() == 0);
  }

  @Test
  @DisplayName("Test PekkoHttpServer flip endpoint")
  void testFlipEndpoint() {
    // Start the PekkoHttpServer actor
    testKit.spawn(PekkoHttpServer.create());

    // Test a valid input (0)
    var response0 = Http.get(testKit.system().classicSystem())
        .singleRequest(HttpRequest.POST("http://localhost:8080/flip").withEntity(HttpEntities.create("0")))
        .toCompletableFuture()
        .join();
    assertEquals(StatusCodes.OK, response0.status());
    assertEquals("1",
        response0.entity().toStrict(5000, SystemMaterializer.get(testKit.system()).materializer())
            .toCompletableFuture().join().getData().utf8String());

    // Test a valid input (1)
    var response1 = Http.get(testKit.system().classicSystem())
        .singleRequest(HttpRequest.POST("http://localhost:8080/flip").withEntity(HttpEntities.create("1")))
        .toCompletableFuture()
        .join();
    assertEquals(StatusCodes.OK, response1.status());
    assertEquals("0",
        response1.entity().toStrict(5000, SystemMaterializer.get(testKit.system()).materializer())
            .toCompletableFuture().join().getData().utf8String());

    // Test an invalid input (2)
    var responseInvalid = Http.get(testKit.system().classicSystem())
        .singleRequest(HttpRequest.POST("http://localhost:8080/flip").withEntity(HttpEntities.create("2")))
        .toCompletableFuture()
        .join();
    assertEquals(StatusCodes.BAD_REQUEST, responseInvalid.status());
    assertEquals("Invalid input. Only 0 or 1 are supported.",
        responseInvalid.entity()
            .toStrict(5000, SystemMaterializer.get(testKit.system()).materializer())
            .toCompletableFuture().join().getData().utf8String());
  }


  @Test
  void testPekkoHttpServiceActor() {
    testKit.spawn(PekkoHttpServer.create());
    ActorRef<Command> serviceActor = testKit.spawn(
        Behaviors.setup(ctx -> new PekkoHttpServiceActor(ctx, "http://localhost:8080"))
    );

    // Create TestProbe to receive responses
    TestProbe<DevsMessage> responseProbe = testKit.createTestProbe();

    // Send a valid request "0"
    serviceActor.tell(new Request<>("0", responseProbe.getRef(), "request-1"));
    DevsMessage response = responseProbe.receiveMessage();

    // Check if the response is successful and verify the result
    assertTrue(response instanceof HttpServiceActor.SucceffulResponse<?>);
    assertEquals(1, ((SucceffulResponse<?>)response).responseData());

    // Send an invalid request "2"
    serviceActor.tell(new Request<>("2", responseProbe.getRef(), "request-2"));
    DevsMessage errorResponse = responseProbe.receiveMessage();

    // Check if the response is an error
    assertTrue(errorResponse instanceof HttpServiceActor.ErrorResponse);
    assertEquals("Error: HTTP 400 Bad Request - Invalid input. Only 0 or 1 are supported.", ((ErrorResponse)errorResponse).error());
  }

  @Test
  @DisplayName("Test capability for PDEVSSimulator and PDEVSModel to support requests to external"
      + "http service during state transition")
  void externalHttpServiceTest() {
    testKit.spawn(PekkoHttpServer.create());
    ActorRef<Command> serviceActor = testKit.spawn(
        Behaviors.setup(ctx -> new PekkoHttpServiceActor(ctx, "http://localhost:8080"))
    );
    TestProbe<DevsMessage> probe = testKit.createTestProbe();
    HttpCalculatingGeneratorModel generatorModel = new HttpCalculatingGeneratorModel(0, serviceActor);
    ActorRef<DevsMessage> simulator = testKit.spawn(Behaviors.setup(
        context -> new PDevsSimulator<LongSimTime, Integer, GeneratorModel>(generatorModel,
            LongSimTime.builder().t(0L).build(), context)));

    // Initialize and expect next sim time to be 1
    simulator.tell(new SimulationInitMessage(SimulationInit.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0))
        .payload(ModelIdPayload.builder().modelId(GeneratorModel.identifier).build())
        .simulationId(simulationId)
        .messageId("SimulationInit")
        .senderId("TestActor")
        .build(), probe.getRef()));
    DevsMessage receivedMessage = probe.receiveMessage();
    assert (receivedMessage instanceof NextInternalTimeReport<?>);
    NextInternalTimeReport nextTime = (NextInternalTimeReport) receivedMessage;
    assert (nextTime.getNextInternalTime() instanceof LongSimTime);
    assert (((LongSimTime) nextTime.getNextInternalTime()).getT() == 1L);
    assert ("generator".equals(nextTime.getSenderId()));

    // Get output and expect it to be 0
    simulator.tell(RequestOutput.<LongSimTime>builder()
        .eventTime(LongSimTime.create(1))
        .payload(ModelIdPayload.builder().modelId(GeneratorModel.identifier).build())
        .simulationId(simulationId)
        .messageId("RequestOutput")
        .senderId("TestActor")
        .build());
    DevsMessage message2 = probe.receiveMessage();
    assert (message2 instanceof OutputReport<?>);
    OutputReport<LongSimTime> modelOutputMessage = (OutputReport<LongSimTime>) message2;
    assert (modelOutputMessage.getNextInternalTime().getT() == 1L);
    List<PortValue<?>> generatorOutput = modelOutputMessage.getPayload().getOutputs();
    assert ((Integer) generatorOutput.get(0).getValue() == 0);

    // Execute transition and expect next time to be 1
    simulator.tell(ExecuteTransition.<LongSimTime>builder()
        .eventTime(LongSimTime.create(1))
        .payload(ExecuteTransitionPayload.builder().modelId(GeneratorModel.identifier).build())
        .simulationId(simulationId)
        .messageId("ExecuteTransition")
        .senderId("TestActor")
        .build());
    DevsMessage message3 = probe.receiveMessage();
    assert (message3 instanceof TransitionComplete<?>);
    TransitionComplete<LongSimTime> transitionDone = (TransitionComplete<LongSimTime>) message3;
    assert (transitionDone.getEventTime().getT() == 1L);

    // Get output and expect it to be 1
    simulator.tell(RequestOutput.<LongSimTime>builder()
        .eventTime(LongSimTime.create(1))
        .payload(ModelIdPayload.builder().modelId(GeneratorModel.identifier).build())
        .simulationId(simulationId)
        .messageId("RequestOutput")
        .senderId("TestActor")
        .build());
    DevsMessage message4 = probe.receiveMessage();
    assert (message4 instanceof OutputReport<?>);
    OutputReport<LongSimTime> modelOutputMessage4 =
        (OutputReport<LongSimTime>) message4;
    List<PortValue<?>> generatorOutput4 = modelOutputMessage4.getPayload().getOutputs();
    assert ((Integer) generatorOutput4.get(0).getValue() == 1);

    // Execute transition and expect next time to be 2
    simulator.tell(ExecuteTransition.<LongSimTime>builder()
        .eventTime(LongSimTime.create(1))
        .payload(ExecuteTransitionPayload.builder().modelId(GeneratorModel.identifier).build())
        .simulationId(simulationId)
        .messageId("ExecuteTransition")
        .senderId("TestActor")
        .build());
    DevsMessage message5 = probe.receiveMessage();
    assert (message5 instanceof TransitionComplete<?>);
    TransitionComplete<LongSimTime> transitionDone2 = (TransitionComplete<LongSimTime>) message5;
    assert (transitionDone2.getNextInternalTime().getT() == 2L);
    assert (transitionDone2.getEventTime().getT() == 1L);

    // Get output and expect it to be 0
    simulator.tell(RequestOutput.<LongSimTime>builder()
        .eventTime(LongSimTime.create(2))
        .payload(ModelIdPayload.builder().modelId(GeneratorModel.identifier).build())
        .simulationId(simulationId)
        .messageId("RequestOutput")
        .senderId("TestActor")
        .build());
    DevsMessage message6 = probe.receiveMessage();
    assert (message6 instanceof OutputReport<?>);
    OutputReport<LongSimTime> modelOutputMessage6 =
        (OutputReport<LongSimTime>) message6;
    List<PortValue<?>> generatorOutput6 = modelOutputMessage6.getPayload().getOutputs();
    assert ((Integer) generatorOutput6.get(0).getValue() == 0);
  }


}
