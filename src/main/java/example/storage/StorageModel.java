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

package example.storage;

import devs.PDEVSModel;
import devs.Port;
import devs.msg.Bag;
import devs.msg.PortValue;
import devs.msg.time.LongSimTime;

/**
 * Represents a DEVS model implementation for simulating storage behavior. This model processes
 * integer inputs and transitions between internal states based on these inputs, producing outputs
 * that represent specific storage states.
 * <p>
 * The model is defined with the following key characteristics: 1. A unique identifier for this
 * model instance is {@code MODEL_ID = "storage"}. 2. Two ports are defined for communication: - An
 * input port ({@code storageInputPort}) that accepts integer values. - An output port
 * ({@code storageOutputPort}) that produces storage state enumeration values.
 * <p>
 * The model interacts with its environment through: - Transition functions: - Internal: Updates
 * state after specific internal events. - External: Updates state based on received input events. -
 * Confluent: Simultaneously handles internal and external events. - Time advance function: Defines
 * the duration for the next event. - Output function: Produces outputs before an internal state
 * transition.
 */
public class StorageModel extends PDEVSModel<LongSimTime, StorageState> {

  public static final String MODEL_ID = "storage";
  public static final Port<Integer> storageInputPort = new Port<>("INPUT", Integer.class);
  public static final Port<StorageStateEnum> storageOutputPort = new Port<>("OUTPUT",
      StorageStateEnum.class);

  /**
   * Constructs a new instance of the StorageModel class, initializing it with a given state.
   *
   * @param initialState the initial state of the storage model
   */
  public StorageModel(StorageState initialState) {
    super(initialState, MODEL_ID);
  }

  /**
   * Transitions the internal state of the model at the specified simulation time. This method
   * updates the model's state by creating a new instance of {@link StorageState} with the current
   * state's value and a 'hasOutput' flag set to false.
   *
   * @param currentTime the current simulation time represented as a {@link LongSimTime} object
   */
  @Override
  public void internalStateTransitionFunction(LongSimTime currentTime) {
    this.modelState = new StorageState(modelState.getStateValue(), false);
  }

  /**
   * Defines the external state transition logic for the StorageModel. This method updates the
   * model's state based on the provided input at the given simulation time. The method processes
   * the input and transitions the internal state accordingly.
   *
   * @param currentTime  the current simulation time represented as a {@link LongSimTime} object
   * @param storageInput the {@link Bag} containing input values for the model's state transition
   * @throws IllegalArgumentException if the input value is not 0 or 1
   */
  @Override
  public void externalStateTransitionFunction(LongSimTime currentTime, Bag storageInput) {
    int storageValue = getInputValue(storageInput);

    if (storageValue == 0) {
      modelState = new StorageState(StorageStateEnum.S0, true);
    } else if (storageValue == 1) {
      modelState = new StorageState(StorageStateEnum.S1, true);
    } else {
      throw new IllegalArgumentException(
          "Received illegal inputs value of " + storageValue + ". Should be 1 or 2.");
    }
  }

  /**
   * Type safe way of getting value from input ports.
   *
   * @param storageInput the bag of inputs
   * @return In this case, the integer value for the INPUT (only) port
   */
  int getInputValue(Bag storageInput) {
    // Check to make sure only one input come in
    if (storageInput.getPortValueList().size() == 1) {
      PortValue<?> pv = storageInput.getPortValueList().get(0);
      // Check to ensure the PortValue identifier is "INPUT"
      if (pv.getPortIdentifier().equals(storageInputPort.getPortIdentifier())) {
        return storageInputPort.getValue(pv);
      } else {
        throw new IllegalArgumentException(
            "StorageModel input port should be of class StorageInputPort, not "
                + pv.getClass().getCanonicalName());
      }
    } else {
      throw new IllegalArgumentException(
          "StorageModel external transition requires one input at a time");
    }
  }

  /**
   * Handles the confluent state transition function of the StorageModel. This method is invoked
   * when an external input is received at the exact moment the model is scheduled to perform an
   * internal transition. It combines the effects of both internal and external state transitions.
   *
   * @param currentTime   the current simulation time represented as a {@link LongSimTime} object
   * @param storageInputs the {@link Bag} containing input values for the model's state transition
   */
  @Override
  public void confluentStateTransitionFunction(LongSimTime currentTime, Bag storageInputs) {
    this.externalStateTransitionFunction(currentTime, storageInputs);
  }

  /**
   * Calculates the time until the next internal event for the simulation model. This method
   * determines the time advance based on the current simulation time and the state of the model. If
   * the model has an output, the time advance will be zero (returning the current simulation time).
   * Otherwise, it sets the time to a maximum value, indicating no imminent event.
   *
   * @param currentTime the current simulation time represented as a {@link LongSimTime} object
   * @return the calculated next internal event time as a {@link LongSimTime} object
   */
  @Override
  public LongSimTime timeAdvanceFunction(LongSimTime currentTime) {
    if (modelState.getHasOutput()) {
      return LongSimTime.builder().t(0L).build();
    } else {
      return LongSimTime.builder().t(Long.MAX_VALUE).build().minus(currentTime);
    }
  }

  /**
   * Generates the output of the StorageModel when the output function is invoked. This method
   * constructs a {@link Bag} containing the port-value pairs based on the current state of the
   * model.
   *
   * @return a {@link Bag} object containing the output values for the storage output port
   */
  @Override
  public Bag outputFunction() {
    return Bag.builder()
        .addPortValueList(storageOutputPort.createPortValue(modelState.getStateValue())).build();
  }
}
