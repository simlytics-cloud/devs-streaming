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

package devs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import devs.msg.DevsMessage;
import devs.msg.time.SimTime;
import devs.utils.ModelUtils;

/**
 * A factory to create a PDevsCoordinator for a DEVS coupled model consisting of subordinate atomic
 * models.
 */
public class CoupledModelFactory<T extends SimTime> extends LoggingSimulatorProvider<T> {

  protected final String modelIdentifier;
  protected final List<SimulatorProvider<T>> simulatorProviders;
  protected final PDevsCouplings couplings;

  /**
   * Constructs a CoupledModelFactory.
   *
   * @param modelIdentifier the unique string identifying this model
   * @param couplings a list of PDevsCouplings used to coupled subordinate DEVS models
   */
  public CoupledModelFactory(String modelIdentifier, List<SimulatorProvider<T>> simulatorProviders,
      PDevsCouplings couplings) {
    this.modelIdentifier = modelIdentifier;
    this.simulatorProviders = simulatorProviders;
    this.couplings = couplings;
    this.loggingModels = new ArrayList<>();
  }

  /**
   * Constructs a CoupledModelFactory.
   *
   * @param modelIdentifier the unique string identifying this model
   * @param simulatorProviders a list of SimulationProviders for subordinate DEVS models
   * @param couplings a list of PDevsCouplings used to coupled subordinate DEVS models
   * @param loggingModels a list of logging models defined by their model indentifiers
   */
  public CoupledModelFactory(String modelIdentifier, List<SimulatorProvider<T>> simulatorProviders,
      PDevsCouplings couplings, List<String> loggingModels) {
    this.modelIdentifier = modelIdentifier;
    this.simulatorProviders = simulatorProviders;
    this.couplings = couplings;
    this.loggingModels = loggingModels;
  }


  /**
   * Creates the PDevsCoordinator for the coupled model.
   *
   * @param initialTime the initial time for the simulation
   * @return the created PDevsCoordinator
   */
  public Behavior<DevsMessage> create(T initialTime) {
    return Behaviors.setup(context -> {
      Map<String, ActorRef<DevsMessage>> modelSimulators = new HashMap<>();
      for (SimulatorProvider<T> simulatorProvider : simulatorProviders) {
        if (simulatorProvider instanceof LoggingSimulatorProvider<T> loggingSimulatorProvider) {
          loggingSimulatorProvider.setLoggingModels(loggingModels);
        }
        ActorRef<DevsMessage> subordinateModel =
            simulatorProvider.provideSimulator(context, initialTime);
        context.watch(subordinateModel);
        modelSimulators.put(simulatorProvider.getModelIdentifier(), subordinateModel);
      }
      return new PDevsCoordinator<>(modelIdentifier, modelSimulators, couplings, context);
    });
  }

  /**
   * Returns the model identifier of the underlying coupled model.
   */
  public String getModelIdentifier() {
    return modelIdentifier;
  }

  /**
   * Provides the PDevsCoordinator for the underlying coupled model.
   */
  @Override
  public ActorRef<DevsMessage> provideSimulator(ActorContext<DevsMessage> context, T initialTime) {
    return context.spawn(create(initialTime), ModelUtils.toLegalActorName(modelIdentifier));
  }
}
