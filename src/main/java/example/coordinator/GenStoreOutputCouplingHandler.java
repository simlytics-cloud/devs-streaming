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

package example.coordinator;

import devs.OutputCouplingHandler;
import devs.msg.PortValue;
import example.generator.GeneratorModel;
import example.storage.StorageModel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GenStoreOutputCouplingHandler extends OutputCouplingHandler {


  public GenStoreOutputCouplingHandler() {
    super(Optional.empty(), Optional.of("generator"), Optional.empty());
  }

  @Override
  public void handlePortValue(String sender, PortValue<?> portValue,
      Map<String, List<PortValue<?>>> receiverMap,
      List<PortValue<?>> outputMessages) {
    PortValue<Integer> inputPortValue = StorageModel.storageInputPort.createPortValue(
        GeneratorModel.generatorOutputPort.getValue(portValue));
    addInputPortValue(inputPortValue, StorageModel.identifier, receiverMap);
  }
}
