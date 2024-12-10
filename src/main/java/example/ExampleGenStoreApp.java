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

package example;

import devs.*;
import devs.msg.DevsMessage;
import devs.msg.InitSim;
import devs.msg.log.DevsLogMessage;
import devs.msg.time.LongSimTime;
import example.coordinator.GenStoreInputCouplingHandler;
import example.coordinator.GenStoreOutputCouplingHandler;
import example.generator.GeneratorModel;
import example.storage.StorageModel;
import example.storage.StorageState;
import example.storage.StorageStateEnum;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ExampleGenStoreApp extends AbstractBehavior<ExampleGenStoreApp.GenStoreApp> {

  public interface GenStoreApp {
  }

  public static class GenStoreStart implements GenStoreApp {
  }

  public static void main(String[] args) {
    //akka.actor.typed.ActorSystem.create(ExampleGenStoreApp.create(), "ExampleGenStoreApp");
    ActorSystem<GenStoreApp> system =
        ActorSystem.create(ExampleGenStoreApp.create(), "ExampleGenStoreApp");
    system.tell(new GenStoreStart());
  }

  @Override
  public Receive<GenStoreApp> createReceive() {
    ReceiveBuilder<GenStoreApp> genStoreAppReceiveBuilder = newReceiveBuilder();
    genStoreAppReceiveBuilder.onMessage(GenStoreStart.class, this::onStart);
    return genStoreAppReceiveBuilder.build();
  }

  private ExampleGenStoreApp(ActorContext<GenStoreApp> context) {
    super(context);
  }

  protected Behavior<GenStoreApp> onStart(GenStoreStart start) {
    ActorContext<GenStoreApp> context = this.getContext();
    ActorRef<DevsLogMessage> loggingActor = context.spawn(DevsLoggingActor.create(System.out, UUID.randomUUID().toString()), "logger");

    ActorRef<DevsMessage> generator = context.spawn(StateLoggingSimulator.create(
        new GeneratorModel(0),
        LongSimTime.builder().t(0L).build(),
        loggingActor
    ), "generator");

    ActorRef<DevsMessage> storage = context.spawn(StateLoggingSimulator.create(
        new StorageModel(new StorageState(StorageStateEnum.S0)),
        LongSimTime.builder().t(0L).build(),
        loggingActor
    ), "storage");

    Map<String, ActorRef<DevsMessage>> modelSimulators = new HashMap<>();
    modelSimulators.put("generator", generator);
    modelSimulators.put("storage", storage);

    PDevsCouplings genStoreCoupling = new PDevsCouplings(
        Collections.singletonList(new GenStoreInputCouplingHandler()),
        Collections.singletonList(new GenStoreOutputCouplingHandler()));
    ActorRef<DevsMessage> coordinator = context.spawn(PDevsCoordinator.create(
            "coupled", "root", modelSimulators, genStoreCoupling),
        "coordinator");

    ActorRef<DevsMessage> rootCoordinator = context.spawn(RootCoordinator.create(
        LongSimTime.builder().t(3L).build(), coordinator
    ), "root");

    rootCoordinator.tell(InitSim.builder().time(LongSimTime.builder().t(0L).build()).build());

    //context.watch(rootCoordinator);
    return Behaviors.same();
  }

  public static Behavior<GenStoreApp> create() {
    return Behaviors.setup(ExampleGenStoreApp::new);
  }
}
