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

import devs.PDEVSModel;
import devs.Port;
import devs.msg.Bag;
import devs.msg.time.LongSimTime;
import example.storage.StorageStateEnum;

public class RecorderModel extends PDEVSModel<LongSimTime, Void> {

  public static final Port<StorageStateEnum> storageOutput = new Port<>("STORAGE_OUTPUT", 
      StorageStateEnum.class);
  public static Port<Integer> generatorOutput = new Port<>("GENERATOR_OUTPUT", Integer.class);

  public RecorderModel(String sender) {
    super(null, sender);
  }

  @Override
  public void internalStateTransitionFunction(LongSimTime currentTime) {
    return;
  }

  @Override
  public void externalStateTransitionFunction(LongSimTime currentTime, Bag input) {
    return;
  }

  @Override
  public void confluentStateTransitionFunction(LongSimTime currentTime, Bag input) {
    return;
  }

  @Override
  public LongSimTime timeAdvanceFunction(LongSimTime currentTime) {
    return LongSimTime.builder().t(Long.MAX_VALUE).build();
  }

  @Override
  public Bag outputFunction() {
    return Bag.builder().build();
  }
}
