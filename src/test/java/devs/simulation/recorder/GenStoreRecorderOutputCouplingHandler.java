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

package devs.simulation.recorder;

import devs.OutputCouplingHandler;
import devs.msg.PortValue;
import example.generator.GeneratorModel;
import example.storage.StorageModel;
import example.storage.StorageStateEnum;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A concrete implementation of the {@link OutputCouplingHandler} that handles port values from
 * specific models, including a generator and a storage model. It processes the outputs of these
 * models and routes them to the input ports of a recorder model.
 */
public class GenStoreRecorderOutputCouplingHandler extends OutputCouplingHandler {


  public GenStoreRecorderOutputCouplingHandler() {
    super(Optional.empty(), Optional.empty(), Optional.empty());
  }

  /**
   * Processes incoming port values from a sender model and routes them to the appropriate input
   * ports of the recorder model. Depending on the sender's identifier, the method interprets the
   * port value, potentially transforms it, and updates the receiver map to facilitate message
   * passing within the simulation framework.
   *
   * @param sender         the identifier of the model sending the port value. Must match a known
   *                       sender, such as the GeneratorModel or StorageModel.
   * @param portValue      the port value received from the sender model. Contains the value, port
   *                       identifier, and its type.
   * @param receiverMap    a map that associates model identifiers with a list of port values to
   *                       send. This is updated by adding new port values for the "recorder"
   *                       model.
   * @param outputMessages a list of port values to be sent as output messages. This parameter is
   *                       not modified by the method.
   */
  @Override
  public void handlePortValue(String sender, PortValue<?> portValue,
      Map<String, List<PortValue<?>>> receiverMap, List<PortValue<?>> outputMessages) {

    if (sender.equals(GeneratorModel.identifier)) {
      PortValue<Integer> recorderInputValue = RecorderModel.generatorOutput
          .createPortValue(GeneratorModel.generatorOutputPort.getValue(portValue));
      addInputPortValue(recorderInputValue, "recorder", receiverMap);
    } else if (sender.equals(StorageModel.MODEL_ID)) {
      PortValue<StorageStateEnum> recorderInputValue;
      if (portValue.getValue() instanceof String) {
        recorderInputValue = RecorderModel.storageOutput
            .createPortValue(StorageStateEnum.valueOf((String) portValue.getValue()));
      } else {
        recorderInputValue = RecorderModel.storageOutput
            .createPortValue(StorageModel.storageOutputPort.getValue(portValue));
      }
      addInputPortValue(recorderInputValue, "recorder", receiverMap);
    }

  }
}
