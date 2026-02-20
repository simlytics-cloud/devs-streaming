/*
 * DEVS Streaming Framework Java Copyright (C) 2024 simlytics.cloud LLC and DEVS Streaming Framework
 * Java contributors. All rights reserved.
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

package example;

import devs.iso.SimulationInit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Terminated;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.ReceiveBuilder;
import devs.DevsLoggingActor;
import devs.PDevsCoordinator;
import devs.PDevsCouplings;
import devs.RootCoordinator;
import devs.StateLoggingSimulator;
import devs.iso.DevsMessage;
import devs.iso.log.DevsLogMessage;
import devs.iso.log.StopLogger;
import devs.iso.time.LongSimTime;
import example.coordinator.GenStoreInputCouplingHandler;
import example.coordinator.GenStoreOutputCouplingHandler;
import example.generator.GeneratorModel;
import example.storage.StorageModel;
import example.storage.StorageState;
import example.storage.StorageStateEnum;

/**
 * ExampleGenStoreApp is an Pekko-based actor system implementation that simulates a coupled model
 * consisting of a generator and a storage unit. This class orchestrates the simulation lifecycle by
 * initializing the required components, setting up couplings, and launching simulation execution.
 * <p>
 * Features: - Manages DEVS model components like a generator and storage. - Sets up logging using a
 * dedicated DevsLoggingActor. - Establishes couplings between generator and storage components. -
 * Spawns a root coordinator to facilitate the simulation process.
 * <p>
 * This class extends from AbstractBehavior to leverage Pekko typed actor behaviors.
 */
public class ExampleGenStoreApp extends AbstractBehavior<ExampleGenStoreApp.GenStoreApp> {

  /**
   * Marker interface for the GenStore application.
   * <p>
   * This interface is implemented by the primary actor responsible for managing the lifecycle and
   * communication in the GenStore application. Classes implementing this interface are used within
   * the actor system to initialize, coordinate, and simulate the interactions between components of
   * the application.
   * <p>
   * The GenStore application serves as an integration of a generator and storage model connected
   * through defined couplings, facilitating message-driven simulation using the DEVS (Discrete
   * Event System Specification) framework.
   */
  public interface GenStoreApp {

  }

  /**
   * Represents the starting message for initializing the GenStore application.
   * <p>
   * The {@code GenStoreStart} class is used as a trigger in the actor system to initiate the
   * execution of the GenStore application. When sent to the appropriate actor within the system,
   * this message signals the startup of the generator and storage models, logging mechanisms,
   * coupling configurations, and simulation components.
   * <p>
   * This class implements the {@link GenStoreApp} marker interface, indicating its role within the
   * GenStore application framework. It is typically processed in the application's actor behavior
   * to establish all necessary components and begin the simulation.
   */
  public static class GenStoreStart implements GenStoreApp {

  }

  /**
   * The main entry point for the GenStore application. This method initializes the actor system and
   * sends the starting message to kick off the simulation.
   *
   * @param args command-line arguments passed to the application
   */
  public static void main(String[] args) {
    ActorSystem<GenStoreApp> system =
        ActorSystem.create(ExampleGenStoreApp.create(), "ExampleGenStoreApp");
    system.tell(new GenStoreStart());
  }

  /**
   * A reference to an actor responsible for handling and logging DEVS (Discrete Event System
   * Specification) related messages during the execution of the GenStore application.
   * <p>
   * This actor is primarily used to manage logging operations for the simulation components,
   * including generator, storage, and coordination models. It processes messages of type
   * {@code DevsLogMessage}, which may include logging details about state transitions, received
   * messages, or other simulation events.
   * <p>
   * The actor is initialized during the startup process of the GenStore application and is
   * monitored for lifecycle changes. When the root coordinator actor is terminated, the
   * {@code loggingActor} is also stopped as part of the cleanup process.
   */
  ActorRef<DevsLogMessage> loggingActor;

  /**
   * Creates a receive handler for the {@code GenStoreApp} interface.
   * <p>
   * This method defines the behavior of the actor by specifying how it handles messages of type
   * {@code GenStoreStart}. It utilizes a {@code ReceiveBuilder} to define message handlers and
   * builds a {@code Receive} instance to be associated with the actor.
   *
   * @return a {@code Receive} instance that defines the actor's message handling behavior.
   */
  @Override
  public Receive<GenStoreApp> createReceive() {
    ReceiveBuilder<GenStoreApp> genStoreAppReceiveBuilder = newReceiveBuilder();
    genStoreAppReceiveBuilder.onMessage(GenStoreStart.class, this::onStart);
    genStoreAppReceiveBuilder.onSignal(Terminated.class, this::onTerminated);
    return genStoreAppReceiveBuilder.build();
  }

  /**
   * Constructs an instance of {@code ExampleGenStoreApp}.
   *
   * @param context the actor context for the {@code GenStoreApp} actor, used for managing and
   *        interacting with the actor's lifecycle and messaging behavior.
   */
  private ExampleGenStoreApp(ActorContext<GenStoreApp> context) {
    super(context);
  }

  /**
   * Handles the startup process of the GenStore application.
   * <p>
   * This method initializes various actors required for the GenStore simulation, such as the
   * logging actor, the generator model, the storage model, and the coordinator actors. It also sets
   * up couplings between components and starts the simulation by sending an initialization message
   * to the root coordinator.
   *
   * @param start the {@code GenStoreStart} message that triggers the initialization process.
   * @return the same behavior of the actor, indicating that the actor's state remains unchanged.
   */
  protected Behavior<GenStoreApp> onStart(GenStoreStart start) {
    ActorContext<GenStoreApp> context = this.getContext();
    loggingActor =
        context.spawn(DevsLoggingActor.create(System.out, UUID.randomUUID().toString()), "logger");

    context.watch(loggingActor);

    ActorRef<DevsMessage> generator =
        context.spawn(StateLoggingSimulator.createStateLoggingSimulator(new GeneratorModel(0, "generator"),
            LongSimTime.builder().t(0L).build()), "generator");

    ActorRef<DevsMessage> storage = context.spawn(StateLoggingSimulator.createStateLoggingSimulator(
        new StorageModel(new StorageState(StorageStateEnum.S0), "storage"),
        LongSimTime.builder().t(0L).build()), "storage");

    Map<String, ActorRef<DevsMessage>> modelSimulators = new HashMap<>();
    modelSimulators.put("generator", generator);
    modelSimulators.put("storage", storage);

    PDevsCouplings genStoreCoupling =
        new PDevsCouplings(Collections.singletonList(new GenStoreInputCouplingHandler()),
            Collections.singletonList(new GenStoreOutputCouplingHandler()));
    ActorRef<DevsMessage> coordinator = context.spawn(
        PDevsCoordinator.create("coupled", modelSimulators, genStoreCoupling), "coordinator");

    ActorRef<DevsMessage> rootCoordinator = context
        .spawn(RootCoordinator.create(LongSimTime.builder().t(3L).build(), coordinator, "coupled"), "root");

    context.watch(rootCoordinator);

    rootCoordinator.tell(SimulationInit.<LongSimTime>builder()
        .eventTime(LongSimTime.create(0L))
        .simulationId("GenStoreSimulation")
        .messageId("App-SimStoreInit")
        .senderId("App")
        .receiverId("root")
        .build());

    return Behaviors.same();
  }

  /**
   * Handles the termination signal for the {@code ExampleGenStoreApp} actor. This method reacts
   * when a watched actor is terminated, such as stopping associated resources or performing cleanup
   * operations.
   *
   * @param signal the termination signal containing details about the terminated actor.
   * @return the stopped behavior for the actor if the terminated actor is identified as "root",
   *         otherwise returns a stopped behavior by default.
   */
  protected Behavior<GenStoreApp> onTerminated(Terminated signal) {
    if (signal.getRef().path().name().equals("root")) {
      loggingActor.tell(StopLogger.builder().build());
      return Behaviors.same();
    }
    return Behaviors.stopped();
  }

  /**
   * Creates the behavior for the {@code ExampleGenStoreApp} actor system.
   * <p>
   * This method sets up the actor's lifecycle and defines its initial behavior. It uses the
   * {@code Behaviors.setup} factory method to create an instance of the {@code ExampleGenStoreApp}
   * actor.
   *
   * @return a {@code Behavior} instance representing the initial setup of the
   *         {@code ExampleGenStoreApp} actor.
   */
  public static Behavior<GenStoreApp> create() {
    return Behaviors.setup(ExampleGenStoreApp::new);
  }
}
