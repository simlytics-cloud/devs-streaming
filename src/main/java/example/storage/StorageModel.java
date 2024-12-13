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

package example.storage;

import devs.PDEVSModel;
import devs.Port;
import devs.msg.Bag;
import devs.msg.PortValue;
import devs.msg.time.LongSimTime;

public class StorageModel extends PDEVSModel<LongSimTime, StorageState> {

  public static final String MODEL_ID = "storage";
  public static final Port<Integer> storageInputPort = new Port<>("INPUT", Integer.class);
  public static final Port<StorageStateEnum> storageOutputPort = new Port<>("OUTPUT", 
      StorageStateEnum.class);

  public StorageModel(StorageState initialState) {
    super(initialState, MODEL_ID);
  }

  @Override
  public void internalStateTransitionFunction(LongSimTime currentTime) {
    this.modelState = new StorageState(modelState.getStateValue(), false);
  }

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

  @Override
  public void confluentStateTransitionFunction(LongSimTime currentTime, Bag storageInputs) {
    this.externalStateTransitionFunction(currentTime, storageInputs);
  }

  @Override
  public LongSimTime timeAdvanceFunction(LongSimTime currentTime) {
    if (modelState.getHasOutput()) {
      return currentTime;
    } else {
      return LongSimTime.builder().t(Long.MAX_VALUE).build();
    }
  }

  @Override
  public Bag outputFunction() {
    return Bag.builder()
        .addPortValueList(storageOutputPort.createPortValue(modelState.getStateValue())).build();
  }
}
