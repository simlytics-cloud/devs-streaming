/*
 * DEVS Streaming Framework Copyright (C) 2023 simlytics.cloud LLC and DEVS Streaming Framework
 * contributors
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

package example.generator;

import devs.PDEVSModel;
import devs.Port;
import devs.msg.Bag;
import devs.msg.time.LongSimTime;

/**
 * The GeneratorModel class represents a simple PDEVS atomic model that alternates its internal
 * state between 0 and 1. It outputs its current state through a predefined output port. This model
 * is often used to simulate a generator of a periodic signal or discrete transitions.
 * <p>
 * This class extends the PDEVSModel, which provides the abstract foundation for implementing DEVS
 * atomic models. Various lifecycle methods such as state transitions and output generation are
 * overridden to define the behavior of this specific model.
 */
public class GeneratorModel extends PDEVSModel<LongSimTime, Integer> {

  public static String identifier = "generator";


  /**
   * Represents the output port of the GeneratorModel, used for emitting the current state of the
   * model during its execution. The port is identified with the name "OUTPUT" and is associated
   * with the data type {@link Integer}.
   * <p>
   * This port is primarily utilized to convey the periodic state transitions of the GeneratorModel
   * (0 or 1). It is designed to output the value of the model's current state whenever the output
   * function is invoked.
   * <p>
   * The data shared through this port is encapsulated in instances of PortValue.
   */
  public static final Port<Integer> generatorOutputPort = new Port<>("OUTPUT", Integer.class);

  /**
   * Constructs a new instance of the GeneratorModel with the given initial state.
   *
   * @param initialState the initial state of the GeneratorModel. This determines the starting state
   *                     of the model, either 0 or 1, and sets up the behavior of the periodic state
   *                     transitions.
   */
  public GeneratorModel(Integer initialState) {
    super(initialState, identifier);
  }

  /**
   * Defines the internal state transition logic for the GeneratorModel. This method alternates the
   * model's internal state between 0 and 1.
   *
   * @param currentTime the current simulation time. This parameter is typically provided by the
   *                    simulation environment and represents the point in simulated time at which
   *                    the transition is occurring.
   */
  @Override
  public void internalStateTransitionFunction(LongSimTime currentTime) {
    if (modelState == 0) {
      this.modelState = 1;
    } else {
      this.modelState = 0;
    }

  }

  /**
   * Defines the external state transition logic for the model. This method processes external
   * inputs received by the model at a specific simulation time and modifies the model's state
   * accordingly.
   *
   * @param currentTime the current simulation time at which the external state transition occurs.
   *                    It is typically provided by the simulation environment.
   * @param inputs      a bag of inputs received at the current simulation time. These inputs are
   *                    processed to influence the model's state during this transition.
   */
  @Override
  public void externalStateTransitionFunction(LongSimTime currentTime, Bag inputs) {

  }

  /**
   * Handles the confluent state transition logic for the model. This method is invoked when both
   * internal and external state transitions are scheduled to occur simultaneously. It allows the
   * model to resolve and handle such scenarios by defining appropriate logic for state updates.
   *
   * @param currentTime the current simulation time at which the confluent state transition occurs.
   *                    Typically provided by the simulation environment.
   * @param inputs      a bag of inputs received at the current simulation time. These inputs are
   *                    processed in conjunction with the internal transition logic.
   */
  @Override
  public void confluentStateTransitionFunction(LongSimTime currentTime, Bag inputs) {

  }

  /**
   * Determines the next time advance value of the model based on its current state and simulation
   * time. This method computes the time duration until the next event or state transition in the
   * model.
   *
   * @param currentTime the current simulation time provided by the simulation environment.
   *                    Represents the point in simulated time at which the time advance is being
   *                    calculated.
   * @return a LongSimTime instance representing the time until the next state transition. If the
   * model's state is 1, the same current time is returned. Otherwise, it returns the current time
   * incremented by 1.
   */
  @Override
  public LongSimTime timeAdvanceFunction(LongSimTime currentTime) {
    if (modelState == 1) {
      return currentTime;
    } else {
      return LongSimTime.builder().t(currentTime.getT() + 1).build();
    }
  }

  /**
   * Produces the output of the current state of the model. The output is encapsulated in a Bag
   * object, which contains port-value pairs representing the output values generated by the model
   * at the current simulation step.
   *
   * @return a Bag object containing the output port-value pairs, constructed using the model's
   * state and the generator output port.
   */
  @Override
  public Bag outputFunction() {
    return Bag.builder().addPortValueList(generatorOutputPort.createPortValue(modelState)).build();
  }
}
